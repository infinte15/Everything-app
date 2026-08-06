import 'package:flutter/material.dart';

import '../../../models/bank_connection.dart';
import '../../../theme/finance_theme.dart';
import '../../../theme/kinetic_theme.dart';

/// Sichtbarer Zustand einer Bankverbindung, die Aufmerksamkeit braucht.
///
/// Eine Zustimmung läuft nach 90 bis 180 Tagen ab und lässt sich nicht
/// verlängern. Ohne diesen Hinweis versiegt der Abruf stumm, und der Nutzer
/// hält Monate alte Zahlen für aktuell - der schlimmste Fehler, den diese
/// Oberfläche machen kann.
class ConnectionBanner extends StatelessWidget {
  final BankConnection connection;
  final VoidCallback onReconnect;

  const ConnectionBanner({
    super.key,
    required this.connection,
    required this.onReconnect,
  });

  @override
  Widget build(BuildContext context) {
    final (icon, text, action, urgent) = _content();

    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.fromLTRB(14, 12, 8, 12),
      decoration: BoxDecoration(
        color: urgent
            ? FinanceTheme.shortfall.withValues(alpha: 0.12)
            : KineticTheme.surfaceElevated,
        borderRadius: BorderRadius.circular(KineticTheme.radius),
      ),
      child: Row(
        children: [
          Icon(
            icon,
            size: 18,
            color: urgent ? FinanceTheme.shortfall : KineticTheme.textSecondary,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              text,
              style: KineticTheme.caption.copyWith(
                color: urgent ? FinanceTheme.shortfall : KineticTheme.textSecondary,
              ),
            ),
          ),
          TextButton(
            onPressed: onReconnect,
            child: Text(
              action,
              style: KineticTheme.caption.copyWith(color: KineticTheme.primary),
            ),
          ),
        ],
      ),
    );
  }

  (IconData, String, String, bool) _content() {
    if (connection.needsReconnect) {
      return (
        Icons.link_off,
        'Die Zustimmung für ${connection.aspspName} ist abgelaufen. '
            'Es kommen keine neuen Buchungen mehr an.',
        'Erneuern',
        true,
      );
    }
    if (connection.hasFailed) {
      return (
        Icons.error_outline,
        connection.lastSyncError ?? 'Der letzte Abruf ist fehlgeschlagen.',
        'Erneut',
        true,
      );
    }
    // Vorwarnung, solange man noch in Ruhe reagieren kann.
    return (
      Icons.schedule,
      'Die Zustimmung für ${connection.aspspName} läuft in '
          '${connection.daysUntilExpiry} Tagen ab. Verlängern geht nicht.',
      'Erneuern',
      false,
    );
  }
}
