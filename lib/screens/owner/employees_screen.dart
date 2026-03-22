// lib/screens/owner/employees_screen.dart
import 'package:flutter/material.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class EmployeesScreen extends StatefulWidget {
  const EmployeesScreen({super.key});

  @override
  State<EmployeesScreen> createState() => _EmployeesScreenState();
}

class _EmployeesScreenState extends State<EmployeesScreen> {
  final ApiService _api = ApiService();
  bool _loading = true;
  List _employees = [];

  final _dummy = [
    {
      'id': 1,
      'full_name': 'Vikram Patel',
      'phone': '9988776655',
      'email': 'vikram@prms.com',
      'assigned_properties': 4,
      'collections_this_month': 28500,
      'status': 'active'
    },
    {
      'id': 2,
      'full_name': 'Ritu Singh',
      'phone': '9977665544',
      'email': 'ritu@prms.com',
      'assigned_properties': 3,
      'collections_this_month': 18200,
      'status': 'active'
    },
    {
      'id': 3,
      'full_name': 'Manish Verma',
      'phone': '9966554433',
      'email': 'manish@prms.com',
      'assigned_properties': 5,
      'collections_this_month': 41000,
      'status': 'active'
    },
  ];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final res = await _api.get(ApiConfig.ownerEmployees);
    if (!mounted) return;
    setState(() {
      _loading = false;
      _employees = (res.success && res.data != null)
          ? (res.data is List ? res.data : [res.data])
          : _dummy;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.primary,
        title: const Text('Agents / Employees',
            style: TextStyle(color: Colors.white)),
        flexibleSpace: Container(
          decoration: const BoxDecoration(
            gradient: LinearGradient(
              colors: [AppTheme.primary, AppTheme.primaryLight],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () {},
        backgroundColor: AppTheme.primary,
        icon: const Icon(Icons.person_add, color: Colors.white),
        label: const Text('Add Agent',
            style: TextStyle(color: Colors.white)),
      ),
      body: _loading
          ? const Center(
              child: CircularProgressIndicator(color: AppTheme.primary))
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: _employees.length,
                itemBuilder: (_, i) =>
                    _EmployeeCard(emp: _employees[i], rank: i + 1),
              ),
            ),
    );
  }
}

class _EmployeeCard extends StatelessWidget {
  final Map emp;
  final int rank;

  const _EmployeeCard({required this.emp, required this.rank});

  @override
  Widget build(BuildContext context) {
    final initials = (emp['full_name'] ?? 'E')
        .split(' ')
        .take(2)
        .map((s) => s.isNotEmpty ? s[0].toUpperCase() : '')
        .join();

    final collections =
        double.tryParse(emp['collections_this_month'].toString()) ?? 0.0;
    final assigned = emp['assigned_properties'] ?? 0;

    final colors = [
        AppTheme.primary,
        AppTheme.accentOrange,
        AppTheme.accentPurple
    ];
    final color = colors[rank % colors.length];

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withOpacity(0.05),
              blurRadius: 10,
              offset: const Offset(0, 3))
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Row(
              children: [
                Stack(
                  children: [
                    CircleAvatar(
                      backgroundColor: color.withOpacity(0.15),
                      radius: 26,
                      child: Text(initials,
                          style: TextStyle(
                              color: color,
                              fontWeight: FontWeight.bold,
                              fontSize: 16)),
                    ),
                    if (rank <= 3)
                      Positioned(
                        bottom: 0,
                        right: 0,
                        child: Container(
                          width: 16,
                          height: 16,
                          decoration: BoxDecoration(
                            color: rank == 1
                                ? Colors.amber
                                : rank == 2
                                    ? Colors.grey[400]
                                    : Colors.brown[300],
                            shape: BoxShape.circle,
                          ),
                          child: Center(
                            child: Text(
                              '#$rank',
                              style: const TextStyle(
                                  fontSize: 8,
                                  color: Colors.white,
                                  fontWeight: FontWeight.bold),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(emp['full_name'] ?? '',
                          style: const TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.bold,
                              color: AppTheme.textDark)),
                      Text(emp['phone'] ?? '',
                          style: const TextStyle(
                              fontSize: 12,
                              color: AppTheme.textGrey)),
                    ],
                  ),
                ),
                StatusBadge(status: emp['status'] ?? 'active'),
              ],
            ),
            const SizedBox(height: 14),
            const Divider(height: 1, color: AppTheme.divider),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: _Metric(
                    label: 'Properties',
                    value: assigned.toString(),
                    icon: Icons.apartment,
                    color: AppTheme.primary,
                  ),
                ),
                Container(
                    width: 1, height: 40, color: AppTheme.divider),
                Expanded(
                  child: _Metric(
                    label: 'This Month',
                    value:
                        '₹${(collections / 1000).toStringAsFixed(1)}K',
                    icon: Icons.account_balance_wallet,
                    color: AppTheme.statusPaid,
                  ),
                ),
                Container(
                    width: 1, height: 40, color: AppTheme.divider),
                Expanded(
                  child: _Metric(
                    label: 'Actions',
                    value: '',
                    icon: Icons.more_horiz,
                    color: AppTheme.textGrey,
                    isAction: true,
                    onTap: () {},
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color color;
  final bool isAction;
  final VoidCallback? onTap;

  const _Metric({
    required this.label,
    required this.value,
    required this.icon,
    required this.color,
    this.isAction = false,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Column(
        children: [
          Icon(icon, size: 18, color: color),
          const SizedBox(height: 4),
          if (!isAction)
            Text(value,
                style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: color)),
          Text(label,
              style: const TextStyle(
                  fontSize: 11, color: AppTheme.textGrey)),
        ],
      ),
    );
  }
}
