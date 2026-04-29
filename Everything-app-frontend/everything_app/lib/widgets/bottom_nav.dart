import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

const _navBackground = Color(0xFF121212);
const _primary = Color(0xFF5856D6);
const _onSurfaceVariant = Color(0xFFA0A0A0);
const _onPrimary = Color(0xFFFFFFFF);

class MainScaffold extends StatelessWidget {
  final Widget child;
  const MainScaffold({super.key, required this.child});

  int _getIndex(BuildContext context) {
    final location = GoRouterState.of(context).uri.toString();
    if (location.startsWith('/calendar')) return 1;
    if (location.startsWith('/spaces')) return 3; // Spaces is index 3
    if (location.startsWith('/create')) return 2; // Create is index 2
    if (location.startsWith('/settings')) return 4;
    return 0; // Home is index 0
  }

  @override
  Widget build(BuildContext context) {
    final currentIndex = _getIndex(context);
    
    return Scaffold(
      backgroundColor: _navBackground,
      body: child,
      bottomNavigationBar: Container(
        height: 80,
        decoration: const BoxDecoration(
          color: _navBackground,
          border: Border(top: BorderSide(color: Color(0xFF1E1E1E), width: 1)),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            _NavItem(
              icon: Icons.home,
              isSelected: currentIndex == 0,
              onTap: () => context.go('/home'),
            ),
            _NavItem(
              icon: Icons.calendar_today,
              isSelected: currentIndex == 1,
              onTap: () => context.go('/calendar'),
            ),
            _NavItem(
              icon: Icons.add,
              isSelected: currentIndex == 2,
              onTap: () => context.go('/create'),
            ),
            _NavItem(
              icon: Icons.grid_view,
              isSelected: currentIndex == 3,
              onTap: () => context.go('/spaces'),
            ),
            _NavItem(
              icon: Icons.settings,
              isSelected: currentIndex == 4,
              onTap: () => context.go('/settings'),
            ),
          ],
        ),
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  final IconData icon;
  final bool isSelected;
  final VoidCallback onTap;

  const _NavItem({
    required this.icon,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: Container(
        width: 48,
        height: 48,
        decoration: BoxDecoration(
          color: isSelected ? _primary : Colors.transparent,
          borderRadius: BorderRadius.zero,
        ),
        child: Center(
          child: Icon(
            icon,
            color: isSelected ? _onPrimary : _onSurfaceVariant,
            size: 24,
          ),
        ),
      ),
    );
  }
}