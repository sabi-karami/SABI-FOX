import 'package:flutter/material.dart';

import '../core/open_in_browser.dart';
import '../data/browser_auth.dart';
import '../data/fxa_auth_repository.dart';
import '../data/token_store.dart';

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
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  Future<void> _start() async {
    final attempt = BrowserAuthAttempt.create();
    setState(() { _attempt = attempt; _error = null; _urlController.clear(); _busy = true; });
    final opened = await openInBrowser(attempt.authorizeUrl.toString());
    if (!mounted) return;
    setState(() { _busy = false; if (!opened) _error = 'Could not open the browser. Check your default browser.'; });
  }

  Future<void> _finish() async {
    final attempt = _attempt;
    if (attempt == null || _busy) return;
    setState(() { _busy = true; _error = null; });
    try {
      await _auth.completeBrowserAuthorization(attempt, _urlController.text);
      _urlController.clear();
      if (!mounted) return;
      widget.onSignedIn();
    } on FormatException catch (e) {
      if (mounted) setState(() { _busy = false; _error = e.message; });
    } catch (_) {
      _urlController.clear();
      if (mounted) setState(() { _attempt = null; _busy = false; _error = 'Authorization could not be completed. Please start again.'; });
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Scaffold(body: SafeArea(child: Center(child: SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 24),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Icon(Icons.public_rounded, size: 64, color: colors.primary),
        const SizedBox(height: 16),
        Text('SABI-FOX', textAlign: TextAlign.center, style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
        const SizedBox(height: 12),
        const Text('Browser sign-in preview', textAlign: TextAlign.center),
        const SizedBox(height: 12),
        const Text('Sign in on Mozilla’s official website. Do not enter your Mozilla password or email code into this app.', textAlign: TextAlign.center),
        const SizedBox(height: 20),
        FilledButton(onPressed: _busy ? null : _start, child: Text(_attempt == null ? 'Open Mozilla sign-in' : 'Start again')),
        if (_attempt != null) ...[
          const SizedBox(height: 18),
          const Text('After signing in, copy the complete final address from your browser and paste it below. Keep this address private; never share it in chat.'),
          const SizedBox(height: 12),
          TextField(controller: _urlController, obscureText: true, maxLines: 1, autocorrect: false, enableSuggestions: false,
            decoration: const InputDecoration(labelText: 'Final Mozilla address', border: OutlineInputBorder())),
          const SizedBox(height: 14),
          FilledButton.tonal(onPressed: _busy ? null : _finish, child: const Text('Finish sign-in')),
        ],
        if (_busy) const Padding(padding: EdgeInsets.all(12), child: Center(child: CircularProgressIndicator())),
        if (_error != null) Padding(padding: const EdgeInsets.only(top: 14), child: Text(_error!, style: TextStyle(color: colors.error))),
        const SizedBox(height: 18),
        Text('Experimental • local proxy only • not a complete VPN', textAlign: TextAlign.center, style: Theme.of(context).textTheme.bodySmall),
      ]),
    ))));
  }
}
