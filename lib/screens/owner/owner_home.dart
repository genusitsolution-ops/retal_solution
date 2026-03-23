// lib/screens/owner/owner_home.dart
import 'package:flutter/material.dart';
import '../profile_screen.dart';
import 'owner_dashboard.dart';
import 'properties_screen.dart';
import 'tenants_screen.dart';
import 'employees_screen.dart';
import 'invoices_screen.dart';
import 'allocations_screen.dart';
import 'reports_screen.dart';
import '../../config/theme.dart';

class OwnerHome extends StatefulWidget {
  const OwnerHome({super.key});
  @override
  State<OwnerHome> createState() => _OwnerHomeState();
}

class _OwnerHomeState extends State<OwnerHome> {
  int _idx = 0;

  final List<Widget> _screens = const [
    OwnerDashboard(),
    PropertiesScreen(),
    TenantsScreen(),
    EmployeesScreen(),
    ProfileScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _idx, children: _screens),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(
          boxShadow: [BoxShadow(color: Colors.black12, blurRadius: 12, offset: Offset(0, -2))],
        ),
        child: BottomNavigationBar(
          currentIndex: _idx,
          onTap: (i) => setState(() => _idx = i),
          items: const [
            BottomNavigationBarItem(
                icon: Icon(Icons.dashboard_outlined), activeIcon: Icon(Icons.dashboard), label: 'Dashboard'),
            BottomNavigationBarItem(
                icon: Icon(Icons.apartment_outlined), activeIcon: Icon(Icons.apartment), label: 'Properties'),
            BottomNavigationBarItem(
                icon: Icon(Icons.people_outline), activeIcon: Icon(Icons.people), label: 'Tenants'),
            BottomNavigationBarItem(
                icon: Icon(Icons.badge_outlined), activeIcon: Icon(Icons.badge), label: 'Agents'),
            BottomNavigationBarItem(
                icon: Icon(Icons.person_outline), activeIcon: Icon(Icons.person), label: 'Profile'),
          ],
        ),
      ),
      // FAB for extra features
      floatingActionButton: _idx == 0 ? FloatingActionButton(
        onPressed: () => _showMoreMenu(),
        backgroundColor: AppTheme.primary,
        mini: true,
        child: const Icon(Icons.more_horiz, color: Colors.white),
      ) : null,
    );
  }

  void _showMoreMenu() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
        padding: const EdgeInsets.all(20),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Center(child: Container(width: 40, height: 4,
              decoration: BoxDecoration(color: Colors.grey[300], borderRadius: BorderRadius.circular(2)))),
          const SizedBox(height: 16),
          const Text('More Features', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          GridView.count(
            crossAxisCount: 3,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            crossAxisSpacing: 12,
            mainAxisSpacing: 12,
            childAspectRatio: 1,
            children: [
              _MenuBtn(icon: Icons.receipt_long, label: 'Invoices', color: AppTheme.accentOrange,
                  onTap: () { Navigator.pop(context); Navigator.push(context, MaterialPageRoute(builder: (_) => const InvoicesScreen())); }),
              _MenuBtn(icon: Icons.home_work, label: 'Allocations', color: AppTheme.accentTeal,
                  onTap: () { Navigator.pop(context); Navigator.push(context, MaterialPageRoute(builder: (_) => const AllocationsScreen())); }),
              _MenuBtn(icon: Icons.bar_chart, label: 'Reports', color: AppTheme.accentPurple,
                  onTap: () { Navigator.pop(context); Navigator.push(context, MaterialPageRoute(builder: (_) => const ReportsScreen())); }),
            ],
          ),
          const SizedBox(height: 10),
        ]),
      ),
    );
  }
}

class _MenuBtn extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;
  const _MenuBtn({required this.icon, required this.label, required this.color, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(
          color: color.withOpacity(0.1),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: color.withOpacity(0.2)),
        ),
        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          Icon(icon, color: color, size: 28),
          const SizedBox(height: 6),
          Text(label, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: color)),
        ]),
      ),
    );
  }
}
