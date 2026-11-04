import 'package:flutter/material.dart' hide ConnectionState;
import 'package:flutter/services.dart';

import '../core/app_logger.dart';
import '../core/app_version.dart';
import '../core/byte_format.dart';
import '../data/models.dart';
import '../data/proxy_state_store.dart';
import '../data/settings_store.dart';
import '../data/token_store.dart';
import '../data/update_checker.dart';
import '../l10n/generated/app_localizations.dart';
import '../vpn/vpn_controller.dart';
import 'sabi_brand.dart';
import 'sabi_theme.dart';

/// Main screen, laid out like the Android app: wordmark bar, connect orb,
/// status, live readouts, the local proxy address and the chosen location.
class HomeScreen extends StatefulWidget {
  const HomeScreen({required this.onSignedOut, this.onOpenServers, super.key});

  final VoidCallback onSignedOut;

  /// Switches the shell to the Servers tab.
  final VoidCallback? onOpenServers;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  VpnController get _vpn => VpnController.instance;
  UpdateChecker get _update => UpdateChecker.instance;

  Future<void> _signOut() async {
    final l10n = AppLocalizations.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.signOutDialogTitle),
        content: Text(l10n.signOutDialogBody),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.actionCancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.actionSignOut),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    await _vpn.disconnect('the user signed out');
    try {
      await TokenStore.instance.clear();
    } catch (e) {
      AppLogger.w('HomeScreen', 'could not clear stored tokens', e);
    }
    if (mounted) widget.onSignedOut();
  }

  Future<void> _togglePower() async {
    switch (_vpn.state) {
      case ConnectionState.disconnected:
        await _vpn.connect();
      case ConnectionState.connecting:
        await _vpn.disconnect('cancelled while connecting');
      case ConnectionState.connected:
        await _vpn.disconnect();
    }
  }

  void _cycleTheme() {
    final settings = SettingsStore.instance;
    settings.themeMode = switch (settings.themeMode) {
      ThemeModeSetting.system => ThemeModeSetting.light,
      ThemeModeSetting.light => ThemeModeSetting.dark,
      ThemeModeSetting.dark => ThemeModeSetting.system,
    };
  }

  /// The address a browser or app should use for the local proxy.
  String _proxyEndpoint() {
    final settings = SettingsStore.instance;
    var host = settings.socksBindAddress.trim();
    // A wildcard bind is reachable locally on the loopback address.
    if (host.isEmpty || host == '0.0.0.0') host = '127.0.0.1';
    if (host == '::') host = '::1';
    if (host.contains(':')) host = '[$host]';
    return '$host:${settings.socksPort}';
  }

  Future<void> _copyEndpoint(String endpoint) async {
    final l10n = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    await Clipboard.setData(ClipboardData(text: endpoint));
    messenger.showSnackBar(
      SnackBar(
        content: Text(l10n.homeAddressCopied),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge([
        _vpn,
        ProxyStateStore.instance,
        SettingsStore.instance,
        UpdateChecker.instance,
      ]),
      builder: (context, _) {
        final theme = Theme.of(context);
        final scheme = theme.colorScheme;
        final status = SabiStatusColors.of(context);
        final l10n = AppLocalizations.of(context);
        final state = _vpn.state;
        final connected = state == ConnectionState.connected;
        final connecting = state == ConnectionState.connecting;
        final selected = ProxyStateStore.instance.load();
        final tone = connected
            ? status.connected
            : connecting
            ? status.connecting
            : scheme.outline;
        final lastError = _vpn.lastError;
        final release = _update.latest;
        final endpoint = _proxyEndpoint();

        return Scaffold(
          body: SafeArea(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(18, 10, 18, 24),
              children: [
                _TopBar(
                  themeMode: SettingsStore.instance.themeMode,
                  onToggleTheme: _cycleTheme,
                  onSignOut: _signOut,
                ),
                const SizedBox(height: 14),
                Center(
                  child: SabiPill(
                    icon: Icons.science_outlined,
                    label: l10n.previewBadge,
                    color: scheme.tertiary,
                  ),
                ),
                const SizedBox(height: 16),
                Center(
                  child: SabiConnectOrb(
                    connected: connected,
                    connecting: connecting,
                    ringColor: tone,
                    label: connected || connecting
                        ? l10n.homeDisconnectAction
                        : l10n.homeConnectAction,
                    onPressed: _togglePower,
                  ),
                ),
                const SizedBox(height: 16),
                AnimatedSwitcher(
                  duration: const Duration(milliseconds: 250),
                  child: Text(
                    _headline(l10n),
                    key: ValueKey<String>(_headline(l10n)),
                    textAlign: TextAlign.center,
                    style: theme.textTheme.headlineSmall?.copyWith(
                      color: connected ? status.connected : scheme.onSurface,
                    ),
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  switch (state) {
                    ConnectionState.connected => _connectedSubtitle(
                      selected,
                      l10n,
                    ),
                    ConnectionState.connecting => l10n.homeEstablishingTunnel,
                    ConnectionState.disconnected => l10n.homeTrafficUnprotected,
                  },
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
                if (lastError != null) ...[
                  const SizedBox(height: 14),
                  _NoticeCard(
                    icon: Icons.error_outline_rounded,
                    color: scheme.error,
                    message: lastError,
                  ),
                ],
                if (_update.updateAvailable && release != null) ...[
                  const SizedBox(height: 14),
                  _UpdateCard(
                    release: release,
                    onDownload: _update.openDownload,
                    onDismiss: _update.dismissLatest,
                  ),
                ],
                const SizedBox(height: 20),
                _TelemetryCard(vpn: _vpn, connected: connected),
                const SizedBox(height: 12),
                _ProxyCard(
                  endpoint: endpoint,
                  active: connected,
                  onCopy: () => _copyEndpoint(endpoint),
                ),
                const SizedBox(height: 12),
                _ServerCard(selected: selected, onTap: widget.onOpenServers),
                _SplitTunnelCard(vpn: _vpn),
              ],
            ),
          ),
        );
      },
    );
  }

  /// Short status line under the orb; details go in the subtitle.
  String _headline(AppLocalizations l10n) {
    return switch (_vpn.statusKind) {
      VpnStatusKind.disconnected => l10n.statusDisconnected,
      VpnStatusKind.connecting => l10n.statusConnecting,
      VpnStatusKind.waitingForNetwork => l10n.statusWaitingForNetwork,
      VpnStatusKind.reconnecting => l10n.statusReconnecting,
      VpnStatusKind.tunnelActive ||
      VpnStatusKind.systemProxyActive ||
      VpnStatusKind.proxyActive => l10n.statusConnectedShort,
    };
  }

  String _connectedSubtitle(ProxyCandidate? selected, AppLocalizations l10n) {
    return switch (_vpn.statusKind) {
      // Tunnel mode shows the exit country/city.
      VpnStatusKind.tunnelActive => _locationLabel(selected, l10n),
      // Proxy modes show the local address.
      VpnStatusKind.systemProxyActive ||
      VpnStatusKind.proxyActive => l10n.homeLocalProxy(_vpn.statusDetail),
      _ => '',
    };
  }

  String _locationLabel(ProxyCandidate? selected, AppLocalizations l10n) {
    if (selected == null) return l10n.autoSelectedLocation;
    final country = selected.countryName.isNotEmpty
        ? selected.countryName
        : selected.countryCode;
    final city = selected.cityCode.isNotEmpty ? ' • ${selected.cityCode}' : '';
    return l10n.exitLocation('$country$city');
  }
}

class _TopBar extends StatelessWidget {
  const _TopBar({
    required this.themeMode,
    required this.onToggleTheme,
    required this.onSignOut,
  });

  final ThemeModeSetting themeMode;
  final VoidCallback onToggleTheme;
  final VoidCallback onSignOut;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final (IconData icon, String label) = switch (themeMode) {
      ThemeModeSetting.system => (Icons.brightness_auto_rounded, l10n.themeAuto),
      ThemeModeSetting.light => (Icons.light_mode_rounded, l10n.themeLight),
      ThemeModeSetting.dark => (Icons.dark_mode_rounded, l10n.themeDark),
    };
    return Row(
      children: [
        const Flexible(
          child: FittedBox(
            fit: BoxFit.scaleDown,
            alignment: AlignmentDirectional.centerStart,
            child: SabiWordmark(),
          ),
        ),
        const Spacer(),
        _RoundIconButton(
          icon: icon,
          tooltip: l10n.themeToggleTooltip(label),
          onPressed: onToggleTheme,
        ),
        const SizedBox(width: 8),
        _RoundIconButton(
          icon: Icons.logout_rounded,
          tooltip: l10n.actionSignOut,
          onPressed: onSignOut,
        ),
      ],
    );
  }
}

class _RoundIconButton extends StatelessWidget {
  const _RoundIconButton({
    required this.icon,
    required this.tooltip,
    required this.onPressed,
  });

  final IconData icon;
  final String tooltip;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return IconButton(
      tooltip: tooltip,
      onPressed: onPressed,
      style: IconButton.styleFrom(
        foregroundColor: scheme.onSurfaceVariant,
        backgroundColor: scheme.surfaceContainerHigh.withValues(alpha: 0.7),
        side: BorderSide(color: scheme.outlineVariant),
      ),
      icon: Icon(icon, size: 20),
    );
  }
}

class _NoticeCard extends StatelessWidget {
  const _NoticeCard({
    required this.icon,
    required this.color,
    required this.message,
  });

  final IconData icon;
  final Color color;
  final String message;

  @override
  Widget build(BuildContext context) {
    return SabiCard(
      accent: color,
      padding: const EdgeInsets.all(14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 20, color: color),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              message,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: Theme.of(context).colorScheme.onSurface,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Shown when the GitHub release check found a newer build.
class _UpdateCard extends StatelessWidget {
  const _UpdateCard({
    required this.release,
    required this.onDownload,
    required this.onDismiss,
  });

  final GithubRelease release;
  final Future<void> Function() onDownload;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final l10n = AppLocalizations.of(context);
    final asset = release.assetName;
    final changelog = release.changelog;

    return SabiCard(
      accent: scheme.tertiary,
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              SabiIconBadge(
                icon: Icons.system_update_alt_rounded,
                color: scheme.tertiary,
                size: 34,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  l10n.updateAvailableTitle(release.version),
                  style: theme.textTheme.titleSmall,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            l10n.updateAvailableBody(AppVersion.full),
            style: theme.textTheme.bodySmall?.copyWith(
              color: scheme.onSurfaceVariant,
            ),
          ),
          if (changelog.isNotEmpty) ...[
            const SizedBox(height: 6),
            Text(
              changelog,
              maxLines: 4,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodySmall?.copyWith(
                color: scheme.onSurfaceVariant,
              ),
            ),
          ],
          const SizedBox(height: 4),
          Wrap(
            alignment: WrapAlignment.end,
            spacing: 6,
            children: [
              TextButton(
                onPressed: onDismiss,
                child: Text(l10n.updateActionNotNow),
              ),
              TextButton(
                onPressed: () => onDownload(),
                child: Text(
                  asset == null
                      ? l10n.updateActionOpenRelease
                      : l10n.updateActionDownload(asset),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Download / upload rates and the account quota, in one card like the
/// Android stats card.
class _TelemetryCard extends StatelessWidget {
  const _TelemetryCard({required this.vpn, required this.connected});

  final VpnController vpn;
  final bool connected;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final l10n = AppLocalizations.of(context);
    final remaining = vpn.quotaRemainingBytes;
    final max = vpn.quotaMaxBytes;

    final Widget quota;
    if (remaining == null) {
      quota = Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.data_usage_rounded, size: 18, color: scheme.primary),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              l10n.quotaPending,
              style: theme.textTheme.bodySmall?.copyWith(
                color: scheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      );
    } else {
      final fraction = (max != null && max > 0)
          ? (remaining / max).clamp(0.0, 1.0).toDouble()
          : null;
      quota = Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Icon(Icons.data_usage_rounded, size: 18, color: scheme.primary),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  l10n.homeQuotaLabel,
                  style: theme.textTheme.labelLarge?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            max != null
                ? l10n.quotaLeftOf(formatBytes(remaining), formatBytes(max))
                : l10n.quotaLeft(formatBytes(remaining)),
            style: theme.textTheme.titleSmall,
          ),
          if (fraction != null) ...[
            const SizedBox(height: 10),
            _GradientBar(fraction: fraction),
          ],
        ],
      );
    }

    return SabiCard(
      padding: const EdgeInsets.fromLTRB(14, 14, 14, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: _Metric(
                  icon: Icons.arrow_downward_rounded,
                  color: scheme.primary,
                  label: l10n.statDownload,
                  value: connected
                      ? formatBytesPerSecond(vpn.downloadBytesPerSecond)
                      : '—',
                ),
              ),
              Container(
                width: 1,
                height: 40,
                margin: const EdgeInsets.symmetric(horizontal: 10),
                color: scheme.outlineVariant,
              ),
              Expanded(
                child: _Metric(
                  icon: Icons.arrow_upward_rounded,
                  color: scheme.tertiary,
                  label: l10n.statUpload,
                  value: connected
                      ? formatBytesPerSecond(vpn.uploadBytesPerSecond)
                      : '—',
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          const Divider(),
          const SizedBox(height: 14),
          quota,
        ],
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric({
    required this.icon,
    required this.color,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final Color color;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      children: [
        SabiIconBadge(icon: icon, color: color, size: 34),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                value,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w800,
                ),
              ),
              Text(
                label,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

/// Remaining-quota bar, filled from the reading start like Android's.
class _GradientBar extends StatelessWidget {
  const _GradientBar({required this.fraction});

  final double fraction;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return LayoutBuilder(
      builder: (context, constraints) {
        return Container(
          height: 8,
          alignment: AlignmentDirectional.centerStart,
          decoration: BoxDecoration(
            color: scheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Container(
            width: constraints.maxWidth * fraction,
            height: 8,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              gradient: LinearGradient(
                colors: [scheme.primary, scheme.tertiary],
              ),
            ),
          ),
        );
      },
    );
  }
}

/// The preview only runs a local proxy: show where it listens and say
/// plainly that other Windows traffic is not routed.
class _ProxyCard extends StatelessWidget {
  const _ProxyCard({
    required this.endpoint,
    required this.active,
    required this.onCopy,
  });

  final String endpoint;
  final bool active;
  final VoidCallback onCopy;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = SabiStatusColors.of(context);
    final l10n = AppLocalizations.of(context);
    final tone = active ? status.connected : scheme.onSurfaceVariant;
    return SabiCard(
      padding: const EdgeInsets.fromLTRB(14, 12, 8, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              SabiIconBadge(icon: Icons.lan_outlined, color: tone, size: 34),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.homeProxyCardTitle,
                      style: theme.textTheme.titleSmall,
                    ),
                    Text(
                      'SOCKS5 + HTTP',
                      textDirection: TextDirection.ltr,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: scheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: l10n.homeCopyAddress,
                onPressed: onCopy,
                icon: const Icon(Icons.content_copy_rounded, size: 20),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Padding(
            padding: const EdgeInsetsDirectional.only(end: 6),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                color: scheme.surfaceContainerLowest.withValues(alpha: 0.7),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: tone.withValues(alpha: 0.4)),
              ),
              child: SelectableText(
                endpoint,
                textAlign: TextAlign.center,
                textDirection: TextDirection.ltr,
                style: TextStyle(
                  fontFamily: 'Consolas',
                  fontFamilyFallback: const [sabiFontFamily],
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                  color: active ? status.connected : scheme.onSurface,
                ),
              ),
            ),
          ),
          const SizedBox(height: 10),
          Padding(
            padding: const EdgeInsetsDirectional.only(end: 6),
            child: Text(
              l10n.homeProxyCardBody,
              style: theme.textTheme.bodySmall?.copyWith(
                color: scheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ServerCard extends StatelessWidget {
  const _ServerCard({required this.selected, required this.onTap});

  final ProxyCandidate? selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final l10n = AppLocalizations.of(context);
    final candidate = selected;
    final country = candidate == null
        ? l10n.serverCardRecommended
        : (candidate.countryName.isNotEmpty
              ? candidate.countryName
              : candidate.countryCode);
    final details = candidate == null
        ? l10n.serverCardPickLocation
        : '${candidate.cityCode.isNotEmpty ? '${candidate.cityCode} • ' : ''}'
              '${candidate.authority}';

    return SabiCard(
      onTap: onTap,
      padding: const EdgeInsetsDirectional.fromSTEB(14, 12, 10, 12),
      child: Row(
        children: [
          SabiCountryBadge(countryCode: candidate?.countryCode),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  country,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.titleSmall,
                ),
                const SizedBox(height: 2),
                Text(
                  details,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: scheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
          if (onTap != null)
            Icon(Icons.chevron_right_rounded, color: scheme.onSurfaceVariant),
        ],
      ),
    );
  }
}

/// Confirms that the rule engine is armed, and how much traffic it has moved
/// around the tunnel during this session.
class _SplitTunnelCard extends StatelessWidget {
  const _SplitTunnelCard({required this.vpn});

  final VpnController vpn;

  @override
  Widget build(BuildContext context) {
    final settings = SettingsStore.instance;
    if (!settings.splitTunnelEnabled) return const SizedBox.shrink();
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final l10n = AppLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: SabiCard(
        padding: const EdgeInsets.all(14),
        child: Row(
          children: [
            SabiIconBadge(
              icon: Icons.alt_route_rounded,
              color: scheme.tertiary,
              size: 34,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    l10n.homeSplitTunnelRules(
                      settings.splitTunnelRules.length,
                    ),
                    style: theme.textTheme.titleSmall,
                  ),
                  Text(
                    l10n.homeSplitTunnelBypassed(vpn.directFlowCount),
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: scheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
