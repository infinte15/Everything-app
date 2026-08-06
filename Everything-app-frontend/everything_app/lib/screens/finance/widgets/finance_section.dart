import 'package:flutter/material.dart';

import '../../../theme/kinetic_theme.dart';

/// Abschnittsüberschrift mit optionaler Aktion rechts.
class FinanceSection extends StatelessWidget {
  final String title;
  final String? action;
  final VoidCallback? onAction;

  const FinanceSection({
    super.key,
    required this.title,
    this.action,
    this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 24, 4, 12),
      child: Row(
        children: [
          Expanded(child: Text(title.toUpperCase(), style: KineticTheme.label)),
          if (action != null)
            GestureDetector(
              onTap: onAction,
              child: Text(
                action!,
                style: KineticTheme.caption.copyWith(color: KineticTheme.primary),
              ),
            ),
        ],
      ),
    );
  }
}

/// Leerzustand: eine Zeile, was fehlt, und optional ein Knopf.
///
/// Ein leerer Bereich ohne Text sieht aus wie ein Ladefehler.
class FinanceEmpty extends StatelessWidget {
  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;

  const FinanceEmpty({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 48),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 40, color: KineticTheme.textTertiary),
            const SizedBox(height: 16),
            Text(title, style: KineticTheme.title, textAlign: TextAlign.center),
            const SizedBox(height: 8),
            Text(message, style: KineticTheme.caption, textAlign: TextAlign.center),
            if (actionLabel != null) ...[
              const SizedBox(height: 20),
              FilledButton(onPressed: onAction, child: Text(actionLabel!)),
            ],
          ],
        ),
      ),
    );
  }
}

/// Eine Karte in der Formensprache des Space: Flächenfarbe, 12er-Radius, kein
/// Schatten.
class FinanceCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;
  final Color? background;

  const FinanceCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.onTap,
    this.background,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: background ?? KineticTheme.surface,
      borderRadius: BorderRadius.circular(KineticTheme.radius),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(KineticTheme.radius),
        child: Padding(padding: padding, child: child),
      ),
    );
  }
}
