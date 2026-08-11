import 'package:flutter/material.dart';

import '../../../theme/kinetic_theme.dart';

/// Abschnittsüberschrift mit optionaler Aktion rechts. Wie [FinanceSection] im
/// Finance Space - dieselbe Form in beiden Bereichen.
class RecipeSection extends StatelessWidget {
  const RecipeSection({
    super.key,
    required this.title,
    this.action,
    this.onAction,
    this.padding = const EdgeInsets.fromLTRB(20, 24, 20, 12),
  });

  final String title;
  final String? action;
  final VoidCallback? onAction;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: padding,
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

/// Leerzustand: was fehlt, warum, und was man dagegen tun kann.
///
/// Ein leerer Bereich ohne Text sieht aus wie ein Ladefehler.
class RecipeEmpty extends StatelessWidget {
  const RecipeEmpty({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    this.actionLabel,
    this.onAction,
    this.secondaryLabel,
    this.onSecondary,
  });

  final IconData icon;
  final String title;
  final String message;
  final String? actionLabel;
  final VoidCallback? onAction;
  final String? secondaryLabel;
  final VoidCallback? onSecondary;

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
              const SizedBox(height: 24),
              SizedBox(
                width: 240,
                child: FilledButton(onPressed: onAction, child: Text(actionLabel!)),
              ),
            ],
            if (secondaryLabel != null) ...[
              const SizedBox(height: 8),
              TextButton(
                onPressed: onSecondary,
                child: Text(
                  secondaryLabel!,
                  style: KineticTheme.caption.copyWith(color: KineticTheme.primary),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
