// lib/screens/owner/owner_home.dart
import 'package:flutter/material.dart';
import '../profile_screen.dart';
import 'owner_dashboard.dart';
import 'properties_screen.dart';
import 'tenants_screen.dart';
import 'employees_screen.dart';
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
          boxShadow: [
            BoxShadow(
                color: Colors.black12, blurRadius: 12, offset: Offset(0, -2))
          ],
        ),
        child: BottomNavigationBar(
          currentIndex: _idx,
          onTap: (i) => setState(() => _idx = i),
          items: const [
            BottomNavigationBarItem(
                icon: Icon(Icons.dashboard_outlined),
                activeIcon: Icon(Icons.dashboard),
                label: 'Dashboard'),
            BottomNavigationBarItem(
                icon: Icon(Icons.apartment_outlined),
                activeIcon: Icon(Icons.apartment),
                label: 'Properties'),
            BottomNavigationBarItem(
                icon: Icon(Icons.people_outline),
                activeIcon: Icon(Icons.people),
                label: 'Tenants'),
            BottomNavigationBarItem(
                icon: Icon(Icons.badge_outlined),
                activeIcon: Icon(Icons.badge),
                label: 'Agents'),
            BottomNavigationBarItem(
                icon: Icon(Icons.person_outline),
                activeIcon: Icon(Icons.person),
                label: 'Profile'),
          ],
        ),
      ),
    );
  }
}
