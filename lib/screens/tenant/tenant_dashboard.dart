// lib/screens/tenant/tenant_dashboard.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../providers/auth_provider.dart';
import '../../services/api_service.dart';
import '../../widgets/stat_card.dart';
import '../../widgets/common_widgets.dart';
import 'tenant_invoices_screen.dart';

class TenantDashboard extends StatefulWidget {
  const TenantDashboard({super.key});
  @override
  State<TenantDashboard> createState() => _TenantDashboardState();
}

class _TenantDashboardState extends State<TenantDashboard> {
  final ApiService _api = ApiService();
  bool _loading = true;
  String? _error;
  Map<String, dynamic> _stats = {};
  Map<String, dynamic> _allocation = {};
  List _recentInvoices = [];

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    final res = await _api.get(ApiConfig.tenantDashboard);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (res.success && res.data != null) {
        final data = res.data as Map<String, dynamic>;
        _stats = data['stats'] ?? data['summary'] ?? {};
        _allocation = data['allocation'] ?? data['property'] ?? {};
        _recentInvoices = data['recent_invoices'] ?? data['invoices'] ?? [];
      } else {
        _error = res.message.isNotEmpty ? res.message : 'Failed to load dashboard';
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.read<AuthProvider>();
    final name = auth.userData?['full_name'] ?? 'Tenant';
    return Scaffold(
      backgroundColor: AppTheme.background,
      body: RefreshIndicator(
        onRefresh: _load,
        child: CustomScrollView(
          slivers: [
            SliverAppBar(
              expandedHeight: 170,
              pinned: true,
              backgroundColor: AppTheme.accentTeal,
              flexibleSpace: FlexibleSpaceBar(
                background: Container(
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(
                      colors: [Color(0xFF00838F), Color(0xFF00BCD4)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                  ),
                  child: SafeArea(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(20, 16, 20, 0),
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                          Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            const Text('Welcome back,', style: TextStyle(color: Colors.white70, fontSize: 13)),
                            Text(name.split(' ').first,
                                style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
                          ]),
                          Row(children: [
                            IconButton(icon: const Icon(Icons.refresh, color: Colors.white), onPressed: _load),
                            CircleAvatar(
                              backgroundColor: Colors.white.withOpacity(0.2), radius: 22,
                              child: Text(name[0].toUpperCase(),
                                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 18)),
                            ),
                          ]),
                        ]),
                        const SizedBox(height: 14),
                        if (_allocation.isNotEmpty)
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                            decoration: BoxDecoration(color: Colors.white.withOpacity(0.15),
                                borderRadius: BorderRadius.circular(12)),
                            child: Row(children: [
                              const Icon(Icons.apartment, color: Colors.white70, size: 18),
                              const SizedBox(width: 8),
                              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                Text(_allocation['property_code'] ?? 'Not Assigned',
                                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 14)),
                                Text(_allocation['address'] ?? _allocation['city'] ?? '',
                                    style: const TextStyle(color: Colors.white70, fontSize: 11)),
                              ])),
                              Text('₹${_stats['current_rent'] ?? _allocation['base_rent'] ?? 0}/mo',
                                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 15)),
                            ]),
                          ),
                      ]),
                    ),
                  ),
                ),
              ),
              title: const Text('My Home', style: TextStyle(color: Colors.white)),
            ),
            if (_loading)
              const SliverFillRemaining(child: Center(child: CircularProgressIndicator(color: AppTheme.accentTeal)))
            else if (_error != null)
              SliverFillRemaining(child: ErrorView(message: _error!, onRetry: _load))
            else
              SliverPadding(
                padding: const EdgeInsets.all(16),
                sliver: SliverList(
                  delegate: SliverChildListDelegate([
                    // Pending dues alert
                    if ((double.tryParse(_stats['pending_amount']?.toString() ?? '0') ?? 0.0) > 0) ...[
                      GestureDetector(
                        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const TenantInvoicesScreen())),
                        child: Container(
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            gradient: const LinearGradient(
                                colors: [Color(0xFFFF6D00), Color(0xFFFFAB40)],
                                begin: Alignment.topLeft, end: Alignment.bottomRight),
                            borderRadius: BorderRadius.circular(16),
                          ),
                          child: Row(children: [
                            const Icon(Icons.warning_amber, color: Colors.white, size: 32),
                            const SizedBox(width: 12),
                            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                              const Text('Payment Due', style: TextStyle(color: Colors.white70, fontSize: 12)),
                              Text(
                                '₹${NumberFormat('#,##,###').format(double.tryParse(_stats['pending_amount']?.toString() ?? '0') ?? 0.0)}',
                                style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold),
                              ),
                            ])),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
                              decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(8)),
                              child: const Text('Pay Now',
                                  style: TextStyle(color: AppTheme.accentOrange, fontWeight: FontWeight.bold, fontSize: 13)),
                            ),
                          ]),
                        ),
                      ),
                      const SizedBox(height: 16),
                    ],
                    GridView.count(
                      crossAxisCount: 2, shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      crossAxisSpacing: 12, mainAxisSpacing: 12, childAspectRatio: 1.15,
                      children: [
                        StatCard(
                          title: 'Total Paid',
                          value: '₹${NumberFormat.compact().format(double.tryParse(_stats['total_paid']?.toString() ?? '0') ?? 0.0)}',
                          icon: Icons.check_circle, color: AppTheme.statusPaid,
                        ),
                        StatCard(title: 'Total Invoices', value: '${_stats['total_invoices'] ?? 0}',
                            icon: Icons.receipt_long, color: AppTheme.accentTeal),
                        StatCard(title: 'Pending', value: '${_stats['pending_invoices'] ?? 0}',
                            icon: Icons.pending_actions, color: AppTheme.statusPending,
                            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const TenantInvoicesScreen()))),
                        StatCard(
                          title: 'Since',
                          value: (_allocation['start_date'] ?? _stats['since'] ?? '-').toString().length > 7
                              ? (_allocation['start_date'] ?? '').toString().substring(0, 7)
                              : (_allocation['start_date'] ?? '-').toString(),
                          icon: Icons.calendar_today, color: AppTheme.primary,
                        ),
                      ],
                    ),
                    const SizedBox(height: 20),
                    SectionHeader(
                      title: 'Recent Invoices',
                      actionLabel: 'View All',
                      onAction: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const TenantInvoicesScreen())),
                    ),
                    const SizedBox(height: 12),
                    if (_recentInvoices.isEmpty)
                      const Center(child: Padding(padding: EdgeInsets.all(16),
                          child: Text('No invoices yet', style: TextStyle(color: AppTheme.textGrey))))
                    else
                      ...(_recentInvoices as List).take(5).map((inv) => Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: Container(
                          padding: const EdgeInsets.all(14),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(12),
                            border: Border(left: BorderSide(
                              color: inv['status'] == 'paid' ? AppTheme.statusPaid : AppTheme.statusPending,
                              width: 4,
                            )),
                            boxShadow: const [BoxShadow(color: Colors.black, blurRadius: 6, offset: Offset(0, 2))],
                          ),
                          child: Row(children: [
                            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                              Text(inv['month'] ?? inv['invoice_number'] ?? '',
                                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
                              Text('Due: ${inv['due_date'] ?? ''}',
                                  style: const TextStyle(fontSize: 12, color: AppTheme.textGrey)),
                            ])),
                            Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
                              Text(
                                '₹${NumberFormat('#,##,###').format(double.tryParse(inv['amount']?.toString() ?? '0') ?? 0.0)}',
                                style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppTheme.textDark),
                              ),
                              StatusBadge(status: inv['status'] ?? 'pending'),
                            ]),
                          ]),
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
