import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../core/open_in_browser.dart';
import '../data/browser_auth.dart';
import '../data/fxa_auth_repository.dart';
import '../data/token_store.dart';
import '../l10n/generated/app_localizations.dart';
import 'sabi_brand.dart';

enum _LoginError { browser, invalidAddress, failed, clipboardEmpty }

/// Sign-in happens on Mozilla's official site in the system browser.
class LoginScreen extends StatefulWidget {
  const LoginScreen({required this.onSignedIn, super.key});

  final VoidCallback onSignedIn;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _auth = FxaAuthRepository(TokenStore.instance);
  final _urlController = TextEditingController();
  BrowserAuthAttempt? _attempt;
  _LoginError? _error;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _urlController.addListener(_onChanged);
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _urlController.removeListener(_onChanged);
    _urlController.dispose();
    super.dispose();
  }

  Future<void> _start() async {
    final attempt = BrowserAuthAttempt.create();
    setState(() {
      _attempt = attempt;
      _error = null;
      _urlController.clear();
      _busy = true;
    });
    final opened = await openInBrowser(attempt.authorizeUrl.toString());
    if (!mounted) return;
    setState(() {
      _busy = false;
      if (!opened) _error = _LoginError.browser;
    });
  }

  Future<void> _paste() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    if (!mounted) return;
    final text = data?.text?.trim() ?? '';
    setState(() {
      if (text.isEmpty) {
        _error = _LoginError.clipboardEmpty;
      } else {
        _urlController.text = text;
        _error = null;
      }
    });
  }

  Future<void> _finish() async {
    final attempt = _attempt;
    if (attempt == null || _busy || _urlController.text.trim().isEmpty) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await _auth.completeBrowserAuthorization(
        attempt,
        _urlController.text.trim(),
      );
      _urlController.clear();
      if (!mounted) return;
      widget.onSignedIn();
    } on FormatException {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = _LoginError.invalidAddress;
        });
      }
    } catch (_) {
      _urlController.clear();
      if (mounted) {
        setState(() {
          _attempt = null;
          _busy = false;
          _error = _LoginError.failed;
        });
      }
    }
  }

  String _errorText(AppLocalizations l10n, _LoginError error) {
    switch (error) {
      case _LoginError.browser:
        return l10n.loginErrorBrowser;
      case _LoginError.invalidAddress:
        return l10n.loginErrorInvalidAddress;
      case _LoginError.failed:
        return l10n.loginErrorFailed;
      case _LoginError.clipboardEmpty:
        return l10n.loginErrorClipboardEmpty;
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final l10n = AppLocalizations.of(context);
    final started = _attempt != null;
    final hasText = _urlController.text.trim().isNotEmpty;
    final error = _error;
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SabiBackdrop(
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 460),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Center(child: SabiGlowMark(size: 84)),
                    const SizedBox(height: 14),
                    const Center(
                      child: FittedBox(
                        fit: BoxFit.scaleDown,
                        child: SabiWordmark(fontSize: 26, showMark: false),
                      ),
                    ),
                    const SizedBox(height: 10),
                    Center(
                      child: SabiPill(
                        label: l10n.previewBadge,
                        icon: Icons.science_outlined,
                        color: scheme.tertiary,
                      ),
                    ),
                    const SizedBox(height: 14),
                    Text(
                      l10n.loginSubtitle,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 20),
                    SabiCard(
                      padding: const EdgeInsets.all(18),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          _Step(
                            number: 1,
                            done: started,
                            active: !started,
                            title: l10n.loginStep1Title,
                            body: l10n.loginStep1Body,
                            child: FilledButton.icon(
                              onPressed: _busy ? null : _start,
                              icon: const Icon(Icons.open_in_new_rounded),
                              label: Text(
                                started
                                    ? l10n.loginStartAgain
                                    : l10n.loginOpenBrowser,
                              ),
                            ),
                          ),
                          _Step(
                            number: 2,
                            done: hasText,
                            active: started && !hasText,
                            title: l10n.loginStep2Title,
                            body: l10n.loginStep2Body,
                            child: started
                                ? Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.stretch,
                                    children: [
                                      Directionality(
                                        textDirection: TextDirection.ltr,
                                        child: TextField(
                                          controller: _urlController,
                                          obscureText: true,
                                          maxLines: 1,
                                          autocorrect: false,
                                          enableSuggestions: false,
                                          enabled: !_busy,
                                          onSubmitted: (_) => _finish(),
                                          decoration: InputDecoration(
                                            labelText: l10n.loginAddressLabel,
                                            prefixIcon:
                                                const Icon(Icons.link_rounded),
                                          ),
                                        ),
                                      ),
                                      const SizedBox(height: 8),
                                      Align(
                                        alignment:
                                            AlignmentDirectional.centerStart,
                                        child: TextButton.icon(
                                          onPressed: _busy ? null : _paste,
                                          icon: const Icon(
                                            Icons.content_paste_rounded,
                                          ),
                                          label: Text(l10n.loginPaste),
                                        ),
                                      ),
                                    ],
                                  )
                                : null,
                          ),
                          _Step(
                            number: 3,
                            done: false,
                            active: started && hasText,
                            last: true,
                            title: l10n.loginStep3Title,
                            body: l10n.loginStep3Body,
                            child: started
                                ? FilledButton.tonalIcon(
                                    onPressed:
                                        _busy || !hasText ? null : _finish,
                                    icon: const Icon(Icons.check_rounded),
                                    label: Text(l10n.loginFinish),
                                  )
                                : null,
                          ),
                          if (_busy)
                            const Padding(
                              padding: EdgeInsets.only(top: 12),
                              child: LinearProgressIndicator(),
                            ),
                          if (error != null)
                            Padding(
                              padding: const EdgeInsets.only(top: 12),
                              child: Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Icon(
                                    Icons.error_outline_rounded,
                                    size: 18,
                                    color: scheme.error,
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      _errorText(l10n, error),
                                      style: theme.textTheme.bodySmall
                                          ?.copyWith(color: scheme.error),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),
                    SabiCard(
                      padding: const EdgeInsets.all(14),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          SabiIconBadge(
                            icon: Icons.shield_outlined,
                            color: scheme.secondary,
                            size: 32,
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              l10n.loginPrivacyNote,
                              style: theme.textTheme.bodySmall,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    Text(
                      l10n.loginFooter,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Step extends StatelessWidget {
  const _Step({
    required this.number,
    required this.done,
    required this.active,
    required this.title,
    required this.body,
    this.child,
    this.last = false,
  });

  final int number;
  final bool done;
  final bool active;
  final bool last;
  final String title;
  final String body;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final highlight = done || active;
    final dotColor = done
        ? scheme.secondary
        : (active ? scheme.primary : scheme.surfaceContainerHighest);
    final content = child;
    return Stack(
      children: [
        if (!last)
          PositionedDirectional(
            start: 13,
            top: 30,
            bottom: 0,
            child: Container(width: 2, color: scheme.outlineVariant),
          ),
        Padding(
          padding: EdgeInsets.only(bottom: last ? 0 : 18),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 28,
                height: 28,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: dotColor,
                  shape: BoxShape.circle,
                ),
                child: done
                    ? Icon(Icons.check_rounded,
                        size: 16, color: scheme.onSecondary)
                    : Text(
                        '$number',
                        style: theme.textTheme.labelMedium?.copyWith(
                          color: active
                              ? scheme.onPrimary
                              : scheme.onSurfaceVariant,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      title,
                      style: theme.textTheme.titleSmall?.copyWith(
                        color: highlight ? null : scheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      body,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                    if (content != null) ...[
                      const SizedBox(height: 10),
                      content,
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
