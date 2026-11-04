import 'package:flutter_test/flutter_test.dart';
import 'package:foxyvpn/data/browser_auth.dart';

void main() {
  test('random PKCE and state; only matching official redirect accepted once', () {
    final a = BrowserAuthAttempt.create();
    final b = BrowserAuthAttempt.create();
    expect(a.verifier.length, inInclusiveRange(43, 128));
    expect(a.verifier, isNot(b.verifier));
    expect(a.state, isNot(b.state));
    expect(a.authorizeUrl.queryParameters['code_challenge'], a.challenge);
    expect(a.authorizeUrl.queryParameters['state'], a.state);
    expect(() => a.consumeRedirect('https://attacker.test/oauth/success/${BrowserAuthAttempt.clientId}?state=${a.state}&code=abc'), throwsFormatException);
    expect(() => a.consumeRedirect('${BrowserAuthAttempt.redirect}?state=wrong&code=abc'), throwsFormatException);
    expect(() => a.consumeRedirect('${BrowserAuthAttempt.redirect}?state=${a.state}&code=abc&code=def'), throwsFormatException);
    expect(a.consumeRedirect('${BrowserAuthAttempt.redirect}?state=${a.state}&code=abc'), 'abc');
    expect(() => a.consumeRedirect('${BrowserAuthAttempt.redirect}?state=${a.state}&code=abc'), throwsStateError);
  });
}
