// SABI-FOX sign-in window: a WebView2 (Microsoft Edge) browser for the Mozilla account pages.
// It mirrors the Android WebView: Firefox user agent, the SABI-FOX WebChannel bridge injected
// into every document, and links outside Mozilla opened in the default browser. It talks to
// SABI-FOX with one JSON object per line on stdin (commands) and stdout (events).
using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace SabiFox.SignIn
{
    internal static class Program
    {
        [STAThread]
        private static int Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            var form = new SignInForm();
            Application.Run(form);
            return form.ExitCode;
        }
    }

    internal sealed class SignInForm : Form
    {
        private static readonly string[] CookieUrls =
        {
            "https://accounts.firefox.com",
            "https://api.accounts.firefox.com",
            "https://oauth.accounts.firefox.com",
            "https://vpn.mozilla.org",
        };

        private readonly JavaScriptSerializer json = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
        private readonly object outputLock = new object();
        private readonly StreamWriter output;
        private readonly WebView2 web;
        private readonly ProgressBar progress;
        private bool closingByCommand;
        private bool initialised;
        public int ExitCode;

        public SignInForm()
        {
            output = new StreamWriter(Console.OpenStandardOutput(), new UTF8Encoding(false)) { AutoFlush = true };
            Text = "SABI-FOX";
            ClientSize = new Size(520, 760);
            MinimumSize = new Size(420, 560);
            StartPosition = FormStartPosition.CenterScreen;
            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
            progress = new ProgressBar { Dock = DockStyle.Top, Height = 3, Style = ProgressBarStyle.Marquee, MarqueeAnimationSpeed = 30 };
            web = new WebView2 { Dock = DockStyle.Fill, DefaultBackgroundColor = Color.FromArgb(7, 11, 20) };
            Controls.Add(web);
            Controls.Add(progress);
        }

        protected override void OnLoad(EventArgs e)
        {
            base.OnLoad(e);
            var reader = new Thread(ReadCommands) { IsBackground = true, Name = "stdin" };
            reader.Start();
        }

        private void Send(Dictionary<string, object> message)
        {
            try
            {
                var line = json.Serialize(message);
                lock (outputLock) { output.WriteLine(line); }
            }
            catch { }
        }

        private void ReadCommands()
        {
            try
            {
                using (var input = new StreamReader(Console.OpenStandardInput(), new UTF8Encoding(false)))
                {
                    string line;
                    while ((line = input.ReadLine()) != null)
                    {
                        Dictionary<string, object> command;
                        try { command = json.Deserialize<Dictionary<string, object>>(line); }
                        catch { continue; }
                        if (command == null) continue;
                        var copy = command;
                        Post(() => Handle(copy));
                    }
                }
            }
            catch { }
            // SABI-FOX went away: close the window too.
            Post(() => { closingByCommand = true; Close(); });
        }

        private void Post(Action action)
        {
            try
            {
                if (IsDisposed || !IsHandleCreated) return;
                BeginInvoke(action);
            }
            catch { }
        }

        private static string Str(Dictionary<string, object> map, string key)
        {
            object value;
            return map.TryGetValue(key, out value) && value != null ? Convert.ToString(value) : "";
        }

        private async void Handle(Dictionary<string, object> command)
        {
            switch (Str(command, "cmd"))
            {
                case "init":
                    if (!initialised) { initialised = true; await Initialise(command); }
                    break;
                case "eval":
                    if (web.CoreWebView2 != null)
                    {
                        try { await web.CoreWebView2.ExecuteScriptAsync(Str(command, "script")); } catch { }
                    }
                    break;
                case "cookies":
                    await PushCookies();
                    break;
                case "focus":
                    if (WindowState == FormWindowState.Minimized) WindowState = FormWindowState.Normal;
                    Activate();
                    break;
                case "close":
                    closingByCommand = true;
                    Close();
                    break;
            }
        }

        private static bool IsMozillaHost(string host)
        {
            host = (host ?? "").ToLowerInvariant();
            return host == "accounts.firefox.com" || host.EndsWith(".firefox.com") ||
                   host == "mozilla.org" || host.EndsWith(".mozilla.org");
        }

        private async Task Initialise(Dictionary<string, object> init)
        {
            var title = Str(init, "title");
            if (title.Length > 0) Text = title + " - SABI-FOX";
            if (Str(init, "rtl") == "True") { RightToLeft = RightToLeft.Yes; RightToLeftLayout = true; }
            try
            {
                var env = await CoreWebView2Environment.CreateAsync(null, Str(init, "userData"), new CoreWebView2EnvironmentOptions());
                await web.EnsureCoreWebView2Async(env);
            }
            catch (WebView2RuntimeNotFoundException ex)
            {
                Fail("webview2_missing", ex.Message);
                return;
            }
            catch (Exception ex)
            {
                Fail("init_failed", ex.Message);
                return;
            }

            var core = web.CoreWebView2;
            core.Settings.UserAgent = Str(init, "ua");
            core.Settings.AreDevToolsEnabled = false;
            core.Settings.IsStatusBarEnabled = false;
            core.Settings.AreHostObjectsAllowed = false;
            core.Settings.IsWebMessageEnabled = true;
            core.Settings.IsGeneralAutofillEnabled = true;

            object cookieList;
            if (init.TryGetValue("cookies", out cookieList) && cookieList is IEnumerable)
            {
                foreach (var item in (IEnumerable)cookieList)
                {
                    var cookie = item as Dictionary<string, object>;
                    if (cookie == null) continue;
                    try
                    {
                        var c = core.CookieManager.CreateCookie(Str(cookie, "name"), Str(cookie, "value"), Str(cookie, "domain"), "/");
                        c.IsSecure = true;
                        core.CookieManager.AddOrUpdateCookie(c);
                    }
                    catch { }
                }
            }

            await core.AddScriptToExecuteOnDocumentCreatedAsync(Str(init, "script"));

            core.WebMessageReceived += (s, e) =>
            {
                string data;
                try { data = e.TryGetWebMessageAsString(); } catch { return; }
                Send(new Dictionary<string, object> { { "type", "bridge" }, { "source", e.Source }, { "data", data } });
            };
            core.NavigationStarting += (s, e) =>
            {
                Uri uri;
                if (!Uri.TryCreate(e.Uri, UriKind.Absolute, out uri)) return;
                if (uri.Scheme != Uri.UriSchemeHttp && uri.Scheme != Uri.UriSchemeHttps) return;
                if (IsMozillaHost(uri.Host))
                {
                    progress.Visible = true;
                    return;
                }
                e.Cancel = true;
                Send(new Dictionary<string, object> { { "type", "external" }, { "url", e.Uri } });
            };
            core.NewWindowRequested += (s, e) =>
            {
                e.Handled = true;
                Uri uri;
                if (!Uri.TryCreate(e.Uri, UriKind.Absolute, out uri)) return;
                if (IsMozillaHost(uri.Host)) core.Navigate(e.Uri);
                else Send(new Dictionary<string, object> { { "type", "external" }, { "url", e.Uri } });
            };
            core.SourceChanged += (s, e) => Send(new Dictionary<string, object> { { "type", "url" }, { "url", core.Source } });
            core.DocumentTitleChanged += (s, e) =>
            {
                var t = core.DocumentTitle;
                if (!string.IsNullOrWhiteSpace(t)) Text = t + " - SABI-FOX";
            };
            core.NavigationCompleted += async (s, e) =>
            {
                progress.Visible = false;
                Send(new Dictionary<string, object> { { "type", "loaded" }, { "url", core.Source }, { "ok", e.IsSuccess } });
                await PushCookies();
            };
            core.ProcessFailed += (s, e) =>
            {
                if (e.ProcessFailedKind == CoreWebView2ProcessFailedKind.BrowserProcessExited)
                    Fail("process_failed", "The browser engine stopped unexpectedly");
            };

            core.Navigate(Str(init, "url"));
            Send(new Dictionary<string, object> { { "type", "ready" } });
            Activate();
        }

        private async Task PushCookies()
        {
            var core = web.CoreWebView2;
            if (core == null) return;
            foreach (var url in CookieUrls)
            {
                try
                {
                    var cookies = await core.CookieManager.GetCookiesAsync(url);
                    var list = new List<object>();
                    foreach (var c in cookies)
                    {
                        list.Add(new Dictionary<string, object> { { "name", c.Name }, { "value", c.Value }, { "domain", c.Domain } });
                    }
                    Send(new Dictionary<string, object> { { "type", "cookies" }, { "url", url }, { "cookies", list } });
                }
                catch { }
            }
        }

        private void Fail(string code, string message)
        {
            ExitCode = 3;
            Send(new Dictionary<string, object> { { "type", "error" }, { "code", code }, { "message", message } });
            closingByCommand = true;
            Close();
        }

        protected override void OnFormClosed(FormClosedEventArgs e)
        {
            if (!closingByCommand) Send(new Dictionary<string, object> { { "type", "closed" } });
            base.OnFormClosed(e);
        }
    }
}
