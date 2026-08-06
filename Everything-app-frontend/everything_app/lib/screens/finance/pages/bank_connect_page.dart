import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../models/aspsp.dart';
import '../../../models/bank_account.dart';
import '../../../models/bank_connection.dart';
import '../../../providers/finance_provider.dart';
import '../../../theme/finance_theme.dart';
import '../../../theme/kinetic_theme.dart';
import '../widgets/finance_format.dart';
import '../widgets/finance_section.dart';

/// Konten verbinden und verwalten.
///
/// Zwei Dinge gibt es sonst nirgends im Projekt und beide sind hier unvermeidlich:
///
/// * **[url_launcher] mit [LaunchMode.externalApplication]** - die Anmeldung
///   läuft im echten Browser. In einem eingebetteten WebView könnte der Nutzer
///   nicht prüfen, wem er seine Bankzugangsdaten gibt, und viele Institute
///   lehnen das ohnehin ab.
/// * **[WidgetsBindingObserver]** - die App erfährt sonst nie, dass der Nutzer
///   aus dem Browser zurückkommt. Der Rücksprung landet beim Backend, nicht bei
///   der App; das Einzige, was hier ankommt, ist "wieder im Vordergrund".
class BankConnectPage extends StatefulWidget {
  const BankConnectPage({super.key});

  @override
  State<BankConnectPage> createState() => _BankConnectPageState();
}

class _BankConnectPageState extends State<BankConnectPage>
    with WidgetsBindingObserver {
  List<Aspsp>? _banks;
  String? _loadError;
  String _query = '';

  /// Wird gesetzt, sobald der Browser geöffnet wurde. Ohne dieses Merkmal würde
  /// jedes beliebige Zurückkehren in die App einen Nachladen-Durchlauf auslösen.
  bool _awaitingReturn = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<FinanceProvider>().loadProviderStatus();
    });
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _awaitingReturn) {
      _awaitingReturn = false;
      // Der Erst-Import ist zu diesem Zeitpunkt schon gelaufen: er passiert
      // synchron im Callback, weil die volle Historie nur unmittelbar nach der
      // Zustimmung verfügbar ist.
      context.read<FinanceProvider>().refreshAfterAuthorization();
    }
  }

  Future<void> _loadBanks() async {
    setState(() {
      _banks = null;
      _loadError = null;
    });
    try {
      final banks = await context.read<FinanceProvider>().searchBanks();
      if (mounted) setState(() => _banks = banks);
    } catch (e) {
      if (mounted) setState(() => _loadError = '$e');
    }
  }

  Future<void> _connect(Aspsp bank) async {
    final finance = context.read<FinanceProvider>();
    final messenger = ScaffoldMessenger.of(context);

    final url = await finance.startBankConnection(bank);
    if (url == null) {
      messenger.showSnackBar(
        SnackBar(content: Text(finance.error ?? 'Verbinden nicht möglich.')),
      );
      return;
    }

    final uri = Uri.parse(url);
    final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!opened) {
      messenger.showSnackBar(
        const SnackBar(content: Text('Der Browser ließ sich nicht öffnen.')),
      );
      return;
    }
    _awaitingReturn = true;
  }

  @override
  Widget build(BuildContext context) {
    final finance = context.watch<FinanceProvider>();

    return Theme(
      data: KineticTheme.darkTheme,
      child: Scaffold(
        backgroundColor: KineticTheme.background,
        appBar: AppBar(title: const Text('Konten')),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 40),
          children: [
            if (finance.isDemo) const _DemoNotice(),

            if (finance.connections.isNotEmpty) ...[
              const FinanceSection(title: 'Verbunden'),
              for (final connection in finance.connections)
                _ConnectionCard(
                  connection: connection,
                  accounts: finance.accounts
                      .where((a) => a.connectionId == connection.id)
                      .toList(),
                  onToggleAccount: finance.setAccountSyncEnabled,
                  onDisconnect: () => _confirmDisconnect(connection),
                ),
            ],

            FinanceSection(
              title: finance.connections.isEmpty ? 'Konto verbinden' : 'Weitere Bank',
              action: _banks == null ? null : 'Neu laden',
              onAction: _loadBanks,
            ),

            if (_banks == null && _loadError == null)
              _StartCard(onStart: _loadBanks)
            else if (_loadError != null)
              _ErrorCard(message: _loadError!, onRetry: _loadBanks)
            else
              _BankPicker(
                banks: _banks!,
                query: _query,
                onQueryChanged: (value) => setState(() => _query = value),
                onSelect: _connect,
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _confirmDisconnect(BankConnection connection) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => Theme(
        data: KineticTheme.darkTheme,
        child: AlertDialog(
          backgroundColor: KineticTheme.surfaceElevated,
          title: const Text('Verbindung trennen?'),
          content: Text(
            'Die bereits importierten Buchungen bleiben erhalten - sie sind '
            'deine Historie. Neue kommen ab jetzt nicht mehr an.',
            style: KineticTheme.subtitle,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('Abbrechen'),
            ),
            TextButton(
              onPressed: () => Navigator.pop(dialogContext, true),
              child: Text('Trennen',
                  style: TextStyle(color: FinanceTheme.shortfall)),
            ),
          ],
        ),
      ),
    );

    if (confirmed == true && mounted) {
      await context.read<FinanceProvider>().disconnectBank(connection.id);
    }
  }
}

/// Im Demo-Betrieb sind alle Zahlen erfunden - das muss dastehen, bevor jemand
/// sie für einen Kontostand hält.
class _DemoNotice extends StatelessWidget {
  const _DemoNotice();

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: KineticTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(KineticTheme.radius),
        border: Border.all(color: KineticTheme.divider),
      ),
      child: Row(
        children: [
          const Icon(Icons.science_outlined, size: 18, color: KineticTheme.textSecondary),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              'Testbetrieb: Konten und Buchungen sind erfunden. Für echte '
              'Bankdaten muss enablebanking.provider=live gesetzt sein.',
              style: KineticTheme.caption.copyWith(fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }
}

class _StartCard extends StatelessWidget {
  final VoidCallback onStart;

  const _StartCard({required this.onStart});

  @override
  Widget build(BuildContext context) {
    return FinanceCard(
      padding: const EdgeInsets.all(18),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Buchungen kommen automatisch an, Kategorien werden vergeben und '
            'Abos erkannt.',
            style: KineticTheme.subtitle,
          ),
          const SizedBox(height: 10),
          Text(
            'Die Anmeldung läuft im Browser, direkt bei deiner Bank. Die App '
            'bekommt Lesezugriff, keine Zugangsdaten.',
            style: KineticTheme.caption.copyWith(color: KineticTheme.textTertiary),
          ),
          const SizedBox(height: 18),
          FilledButton.icon(
            onPressed: onStart,
            icon: const Icon(Icons.search, size: 18),
            label: const Text('Bank auswählen'),
          ),
        ],
      ),
    );
  }
}

class _ErrorCard extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;

  const _ErrorCard({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return FinanceCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Bankenliste nicht erreichbar', style: KineticTheme.title),
          const SizedBox(height: 6),
          Text(message, style: KineticTheme.caption),
          const SizedBox(height: 14),
          FilledButton(onPressed: onRetry, child: const Text('Erneut versuchen')),
        ],
      ),
    );
  }
}

/// Suche und Auswahl.
///
/// Die Suche ist Pflicht, kein Komfort: "Sparkasse" ist keine Bank, sondern ein
/// Verbund aus mehreren hundert regionalen Instituten, und jedes davon steht
/// einzeln in der Liste.
class _BankPicker extends StatelessWidget {
  final List<Aspsp> banks;
  final String query;
  final ValueChanged<String> onQueryChanged;
  final ValueChanged<Aspsp> onSelect;

  const _BankPicker({
    required this.banks,
    required this.query,
    required this.onQueryChanged,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    final needle = query.trim().toLowerCase();
    final matches = needle.isEmpty
        ? banks.take(30).toList()
        : banks
            .where((bank) =>
                bank.name.toLowerCase().contains(needle) ||
                (bank.group ?? '').toLowerCase().contains(needle))
            .toList();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        TextField(
          onChanged: onQueryChanged,
          style: KineticTheme.subtitle.copyWith(color: KineticTheme.textPrimary),
          decoration: const InputDecoration(
            hintText: 'Bank suchen, z. B. "Sparkasse Bodensee"',
            prefixIcon: Icon(Icons.search, size: 18, color: KineticTheme.textTertiary),
            isDense: true,
          ),
        ),
        const SizedBox(height: 8),
        if (needle.isEmpty)
          Padding(
            padding: const EdgeInsets.only(left: 4, bottom: 8),
            child: Text(
              '${banks.length} Institute · such nach dem Namen deiner Bank',
              style: KineticTheme.label,
            ),
          ),
        if (matches.isEmpty)
          Padding(
            padding: const EdgeInsets.all(24),
            child: Text('Kein Institut passt auf "$query".',
                style: KineticTheme.caption),
          ),
        for (final bank in matches) _BankRow(bank: bank, onSelect: onSelect),
      ],
    );
  }
}

class _BankRow extends StatelessWidget {
  final Aspsp bank;
  final ValueChanged<Aspsp> onSelect;

  const _BankRow({required this.bank, required this.onSelect});

  @override
  Widget build(BuildContext context) {
    // Genossenschaftsbanken können nur DECOUPLED/EMBEDDED. Sie hier trotzdem zu
    // zeigen und dann in ein leeres Browserfenster zu schicken wäre schlechter,
    // als ehrlich zu sagen, dass es nicht geht.
    final supported = bank.redirectSupported;

    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: FinanceCard(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        onTap: supported ? () => onSelect(bank) : null,
        child: Row(
          children: [
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: KineticTheme.surfaceElevated,
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Icon(Icons.account_balance,
                  size: 15, color: KineticTheme.textTertiary),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    bank.name,
                    style: KineticTheme.title.copyWith(
                      fontSize: 14,
                      color: supported
                          ? KineticTheme.textPrimary
                          : KineticTheme.textTertiary,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (!supported)
                    Text('Anmeldung im Browser wird nicht unterstützt',
                        style: KineticTheme.label.copyWith(fontSize: 9))
                  else if (bank.group != null)
                    Text(bank.group!, style: KineticTheme.label.copyWith(fontSize: 9)),
                ],
              ),
            ),
            if (bank.beta)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: KineticTheme.surfaceHighlight,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text('BETA', style: KineticTheme.label.copyWith(fontSize: 8)),
              ),
            if (supported)
              const Icon(Icons.chevron_right, size: 18, color: KineticTheme.textTertiary),
          ],
        ),
      ),
    );
  }
}

class _ConnectionCard extends StatelessWidget {
  final BankConnection connection;
  final List<BankAccount> accounts;
  final Future<bool> Function(int, bool) onToggleAccount;
  final VoidCallback onDisconnect;

  const _ConnectionCard({
    required this.connection,
    required this.accounts,
    required this.onToggleAccount,
    required this.onDisconnect,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: FinanceCard(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(connection.aspspName, style: KineticTheme.title)),
                IconButton(
                  onPressed: onDisconnect,
                  icon: const Icon(Icons.link_off, size: 18),
                  color: KineticTheme.textTertiary,
                  visualDensity: VisualDensity.compact,
                ),
              ],
            ),
            Text(
              connection.needsReconnect
                  ? 'Zustimmung abgelaufen'
                  : 'gültig noch ${connection.daysUntilExpiry ?? "?"} Tage · '
                      '${FinanceFormat.lastUpdated(connection.lastSyncAt)}',
              style: KineticTheme.caption.copyWith(
                color: connection.needsReconnect
                    ? FinanceTheme.shortfall
                    : KineticTheme.textTertiary,
              ),
            ),
            if (connection.lastSyncError != null) ...[
              const SizedBox(height: 6),
              Text(connection.lastSyncError!,
                  style: KineticTheme.caption.copyWith(color: FinanceTheme.shortfall)),
            ],
            const SizedBox(height: 12),
            for (final account in accounts)
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                dense: true,
                value: account.syncEnabled,
                activeThumbColor: KineticTheme.primary,
                onChanged: (value) => onToggleAccount(account.id, value),
                title: Text(account.label,
                    style: KineticTheme.subtitle.copyWith(fontSize: 14)),
                subtitle: Text(
                  account.currentBalance == null
                      ? 'noch kein Saldo'
                      : FinanceFormat.money(account.currentBalance!),
                  style: KineticTheme.caption.copyWith(fontSize: 12),
                ),
              ),
            if (accounts.length > 1)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(
                  // Ohne diesen Hinweis sieht der Schalter aus wie eine reine
                  // Anzeigeoption.
                  'Abgeschaltete Konten fließen weder in den Kontostand noch in '
                  'die Prognose ein.',
                  style: KineticTheme.caption.copyWith(
                    fontSize: 11,
                    color: KineticTheme.textTertiary,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
