// lib/screens/owner/owner_dashboard.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../providers/auth_provider.dart';
import '../../services/api_service.dart';
import '../../widgets/stat_card.dart';
import '../../widgets/common_widgets.dart';
import 'invoices_screen.dart';
import 'reports_screen.dart';
import 'properties_screen.dart';
import 'tenants_screen.dart';

class OwnerDashboard extends StatefulWidget {
  const OwnerDashboard({super.key});
  @override
  State<OwnerDashboard> createState() => _OwnerDashboardState();
}

class _OwnerDashboardState extends State<OwnerDashboard> {
  final ApiService _api = ApiService();
  bool _loading = true;
  String? _error;
  Map<String, dynamic> _stats = {};
  List _recentInvoices = [];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    final res = await _api.get(ApiConfig.ownerDashboard);
    if (!mounted) return;
    if (res.success && res.data != null) {
      final data = res.data as Map<String, dynamic>;
      setState(() {
        // Try all possible field name formats from API
      final rawStats = data['stats'] ?? data['summary'] ?? data ?? {};
      _stats = {
        'total_properties': rawStats['total_properties'] ?? rawStats['properties'] ?? rawStats['property_count'] ?? 0,
        'occupied_properties': rawStats['occupied_properties'] ?? rawStats['occupied'] ?? rawStats['occupied_count'] ?? 0,
        'total_tenants': rawStats['total_tenants'] ?? rawStats['tenants'] ?? rawStats['tenant_count'] ?? 0,
        'monthly_revenue': rawStats['billed_this_month'] ?? rawStats['monthly_revenue'] ?? rawStats['total_billed'] ?? rawStats['billed'] ?? 0,
        'collected_this_month': rawStats['collected_this_month'] ?? rawStats['collected'] ?? rawStats['monthly_collected'] ?? 0,
        'pending_amount': rawStats['pending_amount'] ?? rawStats['pending_invoices_amount'] ?? rawStats['pending'] ?? 0,
        'total_employees': rawStats['active_agents'] ?? rawStats['total_employees'] ?? rawStats['employees'] ?? rawStats['agent_count'] ?? 0,
        'open_queries': rawStats['open_queries'] ?? rawStats['queries'] ?? 0,
      };
        _recentInvoices = data['recent_invoices'] ?? data['invoices'] ?? [];
        _loading = false;
      });
    } else {
      setState(() {
        _loading = false;
        _error = res.message.isNotEmpty ? res.message : 'Could not load dashboard';
      });
    }
  }

  String _fmt(dynamic v) => NumberFormat.compact().format(
      double.tryParse(v?.toString() ?? '0') ?? 0);

  String _cur(dynamic v) {
    final n = double.tryParse(v?.toString() ?? '0') ?? 0;
    return '₹${NumberFormat('#,##,###').format(n)}';
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.read<AuthProvider>();
    final name = auth.userData?['full_name'] ?? 'Owner';
    final hour = DateTime.now().hour;
    final greeting = hour < 12 ? 'Good Morning' : hour < 17 ? 'Good Afternoon' : 'Good Evening';

    return Scaffold(
      backgroundColor: AppTheme.background,
      body: RefreshIndicator(
        onRefresh: _load,
        color: AppTheme.primary,
        child: CustomScrollView(
          slivers: [
            SliverAppBar(
              expandedHeight: 160,
              floating: false,
              pinned: true,
              backgroundColor: AppTheme.primary,
              flexibleSpace: FlexibleSpaceBar(
                background: Container(
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      colors: [AppTheme.primary, Color(0xFF283593)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                  ),
                  child: SafeArea(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                Text(greeting, style: const TextStyle(color: Colors.white70, fontSize: 13)),
                                Text(name.split(' ').first,
                                    style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
                              ]),
                              Row(children: [
                                IconButton(icon: const Icon(Icons.refresh, color: Colors.white), onPressed: _load),
                                CircleAvatar(
                                  radius: 20,
                                  backgroundColor: Colors.white.withOpacity(0.2),
                                  child: Text(name[0].toUpperCase(),
                                      style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                                ),
                              ]),
                            ],
                          ),
                          const SizedBox(height: 10),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                            decoration: BoxDecoration(
                              color: Colors.white.withOpacity(0.15),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Text(
                              DateFormat('EEEE, d MMM yyyy').format(DateTime.now()),
                              style: const TextStyle(color: Colors.white70, fontSize: 12),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
              title: const Text('Dashboard', style: TextStyle(color: Colors.white)),
            ),
            if (_error != null)
              SliverToBoxAdapter(
                child: Container(
                  margin: const EdgeInsets.all(16),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppTheme.statusPending.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: AppTheme.statusPending.withOpacity(0.3)),
                  ),
                  child: Row(children: [
                    const Icon(Icons.wifi_off, color: AppTheme.statusPending, size: 16),
                    const SizedBox(width: 8),
                    Expanded(child: Text(_error!, style: const TextStyle(fontSize: 12, color: AppTheme.statusPending))),
                    TextButton(onPressed: _load, child: const Text('Retry')),
                  ]),
                ),
              ),
            if (_loading)
              const SliverFillRemaining(
                child: Center(child: CircularProgressIndicator(color: AppTheme.primary)),
              )
            else
              SliverPadding(
                padding: const EdgeInsets.all(16),
                sliver: SliverList(
                  delegate: SliverChildListDelegate([
                    // Revenue card
                    WideStatCard(
                      title: 'Billed This Month',
                      value: _cur(_stats['monthly_revenue']),
                      icon: Icons.account_balance_wallet,
                      startColor: AppTheme.primary,
                      endColor: AppTheme.primaryLight,
                      trend: 'Collected: ${_cur(_stats['collected_this_month'])}',
                    ),
                    const SizedBox(height: 16),
                    GridView.count(
                      crossAxisCount: 2,
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      crossAxisSpacing: 12,
                      mainAxisSpacing: 12,
                      childAspectRatio: 1.1,
                      children: [
                        StatCard(
                          title: 'Properties',
                          value: _fmt(_stats['total_properties'] ?? _stats['properties'] ?? 0),
                          icon: Icons.apartment,
                          color: AppTheme.primary,
                          subtitle: '${_fmt(_stats['occupied_properties'] ?? _stats['occupied'] ?? 0)} occupied',
                          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PropertiesScreen())),
                        ),
                        StatCard(
                          title: 'Active Tenants',
                          value: _fmt(_stats['total_tenants'] ?? _stats['tenants'] ?? 0),
                          icon: Icons.people,
                          color: AppTheme.accentTeal,
                          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const TenantsScreen())),
                        ),
                        StatCard(
                          title: 'Pending Dues',
                          value: _cur(_stats['pending_amount'] ?? _stats['pending'] ?? 0),
                          icon: Icons.pending_actions,
                          color: AppTheme.statusPending,
                          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const InvoicesScreen())),
                        ),
                        StatCard(
                          title: 'Agents',
                          value: _fmt(_stats['total_employees'] ?? _stats['employees'] ?? 0),
                          icon: Icons.badge,
                          color: AppTheme.accentPurple,
                        ),
                      ],
                    ),
                    // Open queries card
                    if ((_stats['open_queries'] ?? 0).toString() != '0')
                      Container(
                        margin: const EdgeInsets.only(top: 12),
                        padding: const EdgeInsets.all(14),
                        decoration: BoxDecoration(
                          color: AppTheme.accent.withOpacity(0.08),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppTheme.accent.withOpacity(0.3)),
                        ),
                        child: Row(children: [
                          const Icon(Icons.help_outline, color: AppTheme.accent, size: 20),
                          const SizedBox(width: 10),
                          Text('Open Queries: ${_stats['open_queries']}',
                              style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppTheme.accent)),
                        ]),
                      ),
                    const SizedBox(height: 20),
                    // Quick Actions
                    const SectionHeader(title: 'Quick Actions'),
                    const SizedBox(height: 12),
                    Row(children: [
                      _QA(icon: Icons.add_home, label: 'Add Property', color: AppTheme.primary,
                          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PropertiesScreen()))),
                      const SizedBox(width: 10),
                      _QA(icon: Icons.person_add, label: 'Add Tenant', color: AppTheme.accentTeal,
                          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const TenantsScreen()))),
                      const SizedBox(width: 10),
                      _QA(icon: Icons.receipt_long, label: 'Invoices', color: AppTheme.accentOrange,
                          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const InvoicesScreen()))),
                      const SizedBox(width: 10),
                      _QA(icon: Icons.bar_chart, label: 'Reports', color: AppTheme.accentPurple, 
                          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const ReportsScreen()))),
                    ]),
                    const SizedBox(height: 20),
                    SectionHeader(
                      title: 'Recent Invoices',
                      actionLabel: 'View All',
                      onAction: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const InvoicesScreen())),
                    ),
                    const SizedBox(height: 12),
                    if (_recentInvoices.isEmpty)
                      const Center(child: Padding(
                        padding: EdgeInsets.all(16),
                        child: Text('No recent invoices', style: TextStyle(color: AppTheme.textGrey)),
                      ))
                    else
                      ..._recentInvoices.take(5).map((inv) => Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: AppListTile(
                          title: inv['tenant_name'] ?? 'Invoice',
                          subtitle: '${inv['invoice_number'] ?? ''} • Due: ${inv['due_date'] ?? ''}',
                          trailing: '₹${NumberFormat('#,##,###').format(double.tryParse(inv['amount']?.toString() ?? '0') ?? 0)}',
                          trailingColor: inv['status'] == 'paid' ? AppTheme.statusPaid : AppTheme.statusPending,
                          leadingWidget: StatusBadge(status: inv['status'] ?? 'pending'),
                          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const InvoicesScreen())),
                        ),
                      )),
                    const SizedBox(height: 20),
                  ]),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _QA extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;
  const _QA({required this.icon, required this.label, required this.color, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 14),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(14),
            boxShadow: [BoxShadow(color: color.withOpacity(0.15), blurRadius: 8, offset: const Offset(0, 3))],
          ),
          child: Column(children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(color: color.withOpacity(0.12), borderRadius: BorderRadius.circular(10)),
              child: Icon(icon, color: color, size: 20),
            ),
            const SizedBox(height: 6),
            Text(label, textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w600, color: AppTheme.textDark)),
          ]),
        ),
      ),
    );
  }
}
