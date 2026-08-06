import 'package:flutter/material.dart';

import '../../../models/bank_account.dart';
import '../../../theme/kinetic_theme.dart';
import 'finance_format.dart';
import 'finance_section.dart';

/// Konten mit Saldo und Abrufzeitpunkt.
///
/// "aktualisiert 14:32" ist kein Beiwerk: erreicht der Abruf das Tageslimit der
/// Bank, sieht der Nutzer hier, dass die Zahl von heute Mittag ist - statt einer
/// Fehlermeldung, die nach Defekt aussieht.
class AccountCard extends StatelessWidget {
  final List<BankAccount> accounts;
  final DateTime? lastSyncAt;
  final bool isSyncing;
  final bool isDemo;
  final VoidCallback onSync;
  final VoidCallback onManage;

  const AccountCard({
    super.key,
    required this.accounts,
    required this.lastSyncAt,
    required this.isSyncing,
    required this.isDemo,
    required this.onSync,
    required this.onManage,
  });

  @override
  Widget build(BuildContext context) {
    final total = accounts
        .where((a) => a.currentBalance != null)
        .fold(0.0, (sum, a) => sum + a.currentBalance!);

    return FinanceCard(
      onTap: onManage,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text('KONTEN', style: KineticTheme.label)),
              if (isDemo) const _DemoBadge(),
              const SizedBox(width: 8),
              _SyncButton(isSyncing: isSyncing, onSync: onSync),
            ],
          ),
          const SizedBox(height: 10),
          Text(FinanceFormat.money(total), style: KineticTheme.figure.copyWith(fontSize: 26)),
          const SizedBox(height: 2),
          Text(FinanceFormat.lastUpdated(lastSyncAt), style: KineticTheme.caption),
          const SizedBox(height: 14),
          for (final account in accounts) _AccountRow(account: account),
        ],
      ),
    );
  }
}

class _AccountRow extends StatelessWidget {
  final BankAccount account;

  const _AccountRow({required this.account});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        children: [
          Icon(
            account.syncEnabled ? Icons.account_balance : Icons.pause_circle_outline,
            size: 15,
            color: account.syncEnabled
                ? KineticTheme.textTertiary
                : KineticTheme.textTertiary.withValues(alpha: 0.5),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              account.label,
              style: KineticTheme.caption.copyWith(
                color: account.syncEnabled
                    ? KineticTheme.textSecondary
                    : KineticTheme.textTertiary,
              ),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          Text(
            account.currentBalance == null
                ? '—'
                : FinanceFormat.money(account.currentBalance!),
            style: KineticTheme.amount.copyWith(fontSize: 14),
          ),
        ],
      ),
    );
  }
}

/// Kennzeichnung des Demo-Betriebs.
///
/// Ohne sie hält man synthetische Zahlen für echte - und das ist der einzige
/// Fehler, den diese Oberfläche wirklich nicht machen darf.
class _DemoBadge extends StatelessWidget {
  const _DemoBadge();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: KineticTheme.surfaceHighlight,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: KineticTheme.divider),
      ),
      child: Text('TESTDATEN', style: KineticTheme.label.copyWith(fontSize: 9)),
    );
  }
}

class _SyncButton extends StatelessWidget {
  final bool isSyncing;
  final VoidCallback onSync;

  const _SyncButton({required this.isSyncing, required this.onSync});

  @override
  Widget build(BuildContext context) {
    if (isSyncing) {
      return const SizedBox(
        width: 16,
        height: 16,
        child: CircularProgressIndicator(strokeWidth: 2, color: KineticTheme.primary),
      );
    }
    return GestureDetector(
      onTap: onSync,
      child: const Icon(Icons.refresh, size: 18, color: KineticTheme.textSecondary),
    );
  }
}
