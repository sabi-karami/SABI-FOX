import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';

/// Preview-only public-client authorization. No password is entered into the app.
class BrowserAuthAttempt {
  BrowserAuthAttempt._(this.verifier, this.state);

  static const clientId = 'a2270f727f45f648';
  static const scope = 'profile https://identity.mozilla.com/apps/vpn';
  static const redirect = 'https://accounts.firefox.com/oauth/success/$clientId';
  static final tokenEndpoint = Uri.https('oauth.accounts.firefox.com', '/v1/token');

  final String verifier;
  final String state;
  final DateTime createdAt = DateTime.now();
  bool _used = false;

  static String _random() {
    final rng = Random.secure();
    return base64Url.encode(List<int>.generate(32, (_) => rng.nextInt(256))).replaceAll('=', '');
  }

  factory BrowserAuthAttempt.create() => BrowserAuthAttempt._(_random(), _random());

  String get challenge => base64Url.encode(sha256.convert(utf8.encode(verifier)).bytes).replaceAll('=', '');

  Uri get authorizeUrl => Uri.https('oauth.accounts.firefox.com', '/v1/authorization', {
    'client_id': clientId, 'scope': scope, 'response_type': 'code',
    'access_type': 'offline', 'redirect_uri': redirect, 'state': state,
    'code_challenge': challenge, 'code_challenge_method': 'S256',
  });

  /// Require the complete redirected URL, including state; reject unrelated hosts.
  String consumeRedirect(String raw) {
    if (_used || DateTime.now().difference(createdAt) > const Duration(minutes: 10)) {
      throw StateError('This login attempt has expired or was already used.');
    }
    if (raw.length > 8192) throw const FormatException('Invalid authorization URL.');
    final uri = Uri.tryParse(raw.trim());
    if (uri == null || uri.scheme != 'https' || uri.host != 'accounts.firefox.com' ||
        uri.port != 443 || uri.userInfo.isNotEmpty || uri.fragment.isNotEmpty ||
        uri.path != '/oauth/success/$clientId') {
      throw const FormatException('Paste the complete final Mozilla authorization URL.');
    }
    final states = uri.queryParametersAll['state'];
    final codes = uri.queryParametersAll['code'];
    if (states == null || states.length != 1 || states.single != state ||
        codes == null || codes.length != 1 || codes.single.isEmpty || codes.single.length > 2048) {
      throw const FormatException('Authorization URL is missing its code or matching state. Start again if needed.');
    }
    _used = true;
    return codes.single;
  }

  Map<String, String> exchangeForm(String code) => {
    'client_id': clientId, 'grant_type': 'authorization_code', 'code': code,
    'code_verifier': verifier, 'redirect_uri': redirect,
  };
}
