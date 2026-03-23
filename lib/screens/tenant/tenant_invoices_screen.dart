// lib/screens/tenant/tenant_invoices_screen.dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class TenantInvoicesScreen extends StatefulWidget {
  const TenantInvoicesScreen({super.key});
  @override
  State<TenantInvoicesScreen> createState() => _TenantInvoicesScreenState();
}

class _TenantInvoicesScreenState extends State<TenantInvoicesScreen> {
  final ApiService _api = ApiService();
  bool _loading = true;
  String? _error;
  List _invoices = [];

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    final res = await _api.get(ApiConfig.tenantInvoices);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (res.success && res.data != null) {
        _invoices = res.data is List ? res.data : (res.data['invoices'] ?? []);
      } else {
        _error = res.message.isNotEmpty ? res.message : 'Failed to load invoices';
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.accentTeal,
        title: const Text('My Invoices', style: TextStyle(color: Colors.white)),
        flexibleSpace: Container(decoration: const BoxDecoration(
          gradient: LinearGradient(colors: [Color(0xFF00838F), Color(0xFF00BCD4)],
              begin: Alignment.topLeft, end: Alignment.bottomRight),
        )),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: AppTheme.accentTeal))
          : _error != null
              ? ErrorView(message: _error!, onRetry: _load)
              : _invoices.isEmpty
                  ? const EmptyState(icon: Icons.receipt_long_outlined, title: 'No Invoices',
                      subtitle: 'You have no invoices yet')
                  : RefreshIndicator(
                      onRefresh: _load,
                      child: ListView.builder(
                        padding: const EdgeInsets.all(16),
                        itemCount: _invoices.length,
                        itemBuilder: (_, i) {
                          final inv = _invoices[i];
                          final amount = double.tryParse(inv['amount']?.toString() ?? '0') ?? 0.0;
                          final paid = double.tryParse(inv['paid_amount']?.toString() ?? '0') ?? 0.0;
                          final isPaid = inv['status'] == 'paid';
                          final color = isPaid ? AppTheme.statusPaid : AppTheme.statusPending;
                          return Container(
                            margin: const EdgeInsets.only(bottom: 10),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(14),
                              border: Border(left: BorderSide(color: color, width: 4)),
                              boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05),
                                  blurRadius: 8, offset: const Offset(0, 2))],
                            ),
                            child: Padding(
                              padding: const EdgeInsets.all(16),
                              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                                  Text(inv['month'] ?? inv['invoice_number'] ?? '',
                                      style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold,
                                          color: AppTheme.textDark)),
                                  StatusBadge(status: inv['status'] ?? 'pending'),
                                ]),
                                const SizedBox(height: 4),
                                Text('${inv['invoice_number'] ?? ''} • Due: ${inv['due_date'] ?? ''}',
                                    style: const TextStyle(fontSize: 12, color: AppTheme.textGrey)),
                                const SizedBox(height: 12),
                                Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                                  Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                    const Text('Rent Amount', style: TextStyle(fontSize: 11, color: AppTheme.textGrey)),
                                    Text('₹${NumberFormat('#,##,###').format(amount)}',
                                        style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold,
                                            color: AppTheme.textDark)),
                                  ]),
                                  isPaid
                                      ? const Row(children: [
                                          Icon(Icons.check_circle, color: AppTheme.statusPaid, size: 18),
                                          SizedBox(width: 4),
                                          Text('Fully Paid', style: TextStyle(color: AppTheme.statusPaid,
                                              fontWeight: FontWeight.w600, fontSize: 13)),
                                        ])
                                      : Text('Paid: ₹${NumberFormat('#,##,###').format(paid)}',
                                          style: const TextStyle(fontSize: 13, color: AppTheme.textGrey)),
                                ]),
                                if (!isPaid) ...[
                                  const SizedBox(height: 10),
                                  ClipRRect(
                                    borderRadius: BorderRadius.circular(4),
                                    child: LinearProgressIndicator(
                                      value: amount > 0 ? paid / amount : 0,
                                      backgroundColor: AppTheme.divider,
                                      valueColor: AlwaysStoppedAnimation(color),
                                      minHeight: 6,
                                    ),
                                  ),
                                ],
                              ]),
                            ),
                          );
                        },
                      ),
                    ),
    );
  }
}
