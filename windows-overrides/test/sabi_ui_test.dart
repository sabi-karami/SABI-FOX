import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxyvpn/l10n/generated/app_localizations.dart';
import 'package:foxyvpn/ui/login_screen.dart';
import 'package:foxyvpn/ui/sabi_theme.dart';

void main() {
  for (final locale in const [Locale('en'), Locale('fa')]) {
    for (final brightness in Brightness.values) {
      testWidgets('login renders: ${locale.languageCode} ${brightness.name}', (
        tester,
      ) async {
        tester.view.physicalSize = const Size(360, 800);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        await tester.pumpWidget(
          MaterialApp(
            locale: locale,
            theme: buildSabiTheme(brightness),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: LoginScreen(onSignedIn: () {}),
          ),
        );
        await tester.pump();
        expect(find.byType(LoginScreen), findsOneWidget);
        expect(tester.takeException(), isNull);
      });
    }
  }
}
