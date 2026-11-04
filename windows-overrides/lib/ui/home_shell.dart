import 'package:flutter/material.dart';

import '../l10n/generated/app_localizations.dart';
import 'home_screen.dart';
import 'logs_screen.dart';
import 'sabi_brand.dart';
import 'server_list_screen.dart';
import 'settings_screen.dart';

/// Phone-style shell over the aurora backdrop: pages plus a bottom bar.
class HomeShell extends StatefulWidget {
  const HomeShell({required this.onSignedOut, super.key});

  final VoidCallback onSignedOut;

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;

  late final List<Widget> _pages = [
    HomeScreen(
      onSignedOut: widget.onSignedOut,
      onOpenServers: () => setState(() => _index = 1),
    ),
    const ServerListScreen(),
    const SettingsScreen(),
    const LogsScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return SabiBackdrop(
      child: Theme(
        data: theme.copyWith(scaffoldBackgroundColor: Colors.transparent),
        child: Scaffold(
          backgroundColor: Colors.transparent,
          body: IndexedStack(
            index: _index,
            children: [
              for (var i = 0; i < _pages.length; i++)
                TickerMode(enabled: i == _index, child: _pages[i]),
            ],
          ),
          bottomNavigationBar: DecoratedBox(
            decoration: BoxDecoration(
              border: Border(
                top: BorderSide(color: theme.colorScheme.outlineVariant),
              ),
            ),
            child: NavigationBar(
              selectedIndex: _index,
              height: 64,
              labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
              onDestinationSelected: (index) => setState(() => _index = index),
              destinations: [
                NavigationDestination(
                  icon: const Icon(Icons.power_settings_new_rounded),
                  selectedIcon: const Icon(Icons.power_settings_new_rounded),
                  label: l10n.navHome,
                ),
                NavigationDestination(
                  icon: const Icon(Icons.public_outlined),
                  selectedIcon: const Icon(Icons.public_rounded),
                  label: l10n.navServers,
                ),
                NavigationDestination(
                  icon: const Icon(Icons.tune_outlined),
                  selectedIcon: const Icon(Icons.tune_rounded),
                  label: l10n.navSettings,
                ),
                NavigationDestination(
                  icon: const Icon(Icons.receipt_long_outlined),
                  selectedIcon: const Icon(Icons.receipt_long_rounded),
                  label: l10n.navLogs,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
