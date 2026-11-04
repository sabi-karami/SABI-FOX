// sabifox-core is the SABI-FOX engine for Windows: the pinned sing-box MASQUE fork the Android
// app embeds as libbox, wrapped in a tiny stdin/stdout protocol so the configuration and the
// proxy credential never touch the disk.
//
//	sabifox-core check   < config.json            validate, exit status 0 when valid
//	sabifox-core run     < {"token":..,"config":..}  then {"cmd":"token","value":..} / {"cmd":"stop"}
//
// The run command reports {"event":"started"} or {"event":"error","message":..} on stdout and
// writes engine logs to stderr. Closing stdin (for example because SABI-FOX exited or crashed)
// shuts the engine down gracefully, which removes the tunnel routes and the system proxy.
package main

import (
	"bufio"
	"context"
	stdjson "encoding/json"
	"fmt"
	"io"
	"os"
	"os/signal"
	"runtime/debug"
	"sync"
	"syscall"
	"time"

	box "github.com/sagernet/sing-box"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/deprecated"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	transportHttp "github.com/sagernet/sing-box/transport/http"
	"github.com/sagernet/sing/common/json"
	"github.com/sagernet/sing/service"
)

var (
	globalCtx context.Context
	outMu     sync.Mutex
)

func emit(event map[string]string) {
	outMu.Lock()
	defer outMu.Unlock()
	data, _ := stdjson.Marshal(event)
	os.Stdout.Write(append(data, '\n'))
}

func fail(err error) {
	emit(map[string]string{"event": "error", "message": err.Error()})
	fmt.Fprintln(os.Stderr, "FATAL", err.Error())
	os.Exit(1)
}

func parse(content []byte) (option.Options, error) {
	return json.UnmarshalExtendedContext[option.Options](globalCtx, content)
}

func newBox(options option.Options) (*box.Box, context.CancelFunc, error) {
	ctx, cancel := context.WithCancel(service.ExtendContext(globalCtx))
	instance, err := box.New(box.Options{Context: ctx, Options: options})
	if err != nil {
		cancel()
		return nil, nil, err
	}
	return instance, cancel, nil
}

func check() {
	content, err := io.ReadAll(os.Stdin)
	if err != nil {
		fmt.Println("read config:", err)
		os.Exit(1)
	}
	options, err := parse(content)
	if err != nil {
		fmt.Println("decode config:", err)
		os.Exit(1)
	}
	instance, cancel, err := newBox(options)
	if err != nil {
		fmt.Println("create service:", err)
		os.Exit(1)
	}
	instance.Close()
	cancel()
	fmt.Println("ok")
}

type startRequest struct {
	Token  string             `json:"token"`
	Config stdjson.RawMessage `json:"config"`
}

type command struct {
	Cmd   string `json:"cmd"`
	Value string `json:"value"`
}

func run() {
	reader := bufio.NewReaderSize(os.Stdin, 1<<20)
	first, err := reader.ReadBytes('\n')
	if err != nil && len(first) == 0 {
		fail(fmt.Errorf("no configuration received: %w", err))
	}
	var request startRequest
	if err := stdjson.Unmarshal(first, &request); err != nil {
		fail(fmt.Errorf("invalid start request: %w", err))
	}
	options, err := parse(request.Config)
	if err != nil {
		fail(fmt.Errorf("decode config: %w", err))
	}
	// The MASQUE outbound reads the Guardian proxy pass from the dynamic provider, so it must
	// be installed before the outbound is created.
	transportHttp.SetDynamicBearerToken(request.Token)
	request = startRequest{}

	instance, cancel, err := newBox(options)
	if err != nil {
		fail(fmt.Errorf("create service: %w", err))
	}
	if err := instance.Start(); err != nil {
		instance.Close()
		cancel()
		fail(fmt.Errorf("start service: %w", err))
	}
	debug.FreeOSMemory()
	emit(map[string]string{"event": "started"})

	stop := make(chan struct{})
	var once sync.Once
	shutdown := func() { once.Do(func() { close(stop) }) }

	go func() {
		for {
			line, err := reader.ReadBytes('\n')
			if len(line) > 0 {
				var cmd command
				if stdjson.Unmarshal(line, &cmd) == nil {
					switch cmd.Cmd {
					case "token":
						transportHttp.SetDynamicBearerToken(cmd.Value)
					case "stop":
						shutdown()
						return
					}
				}
			}
			if err != nil {
				shutdown()
				return
			}
		}
	}()

	signals := make(chan os.Signal, 1)
	signal.Notify(signals, os.Interrupt, syscall.SIGTERM)
	select {
	case <-stop:
	case <-signals:
	}

	cancel()
	done := make(chan struct{})
	go func() {
		instance.Close()
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(10 * time.Second):
		fmt.Fprintln(os.Stderr, "WARN close timed out")
	}
	transportHttp.SetDynamicBearerToken("")
}

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: sabifox-core check|run|version")
		os.Exit(2)
	}
	globalCtx = include.Context(service.ContextWith(context.Background(), deprecated.NewStderrManager(log.StdLogger())))
	switch os.Args[1] {
	case "check":
		check()
	case "run":
		run()
	case "version":
		fmt.Println("sabifox-core (sing-box", C.Version, ")")
	default:
		fmt.Fprintln(os.Stderr, "unknown command", os.Args[1])
		os.Exit(2)
	}
}
