// lib/screens/owner/invoices_screen.dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class InvoicesScreen extends StatefulWidget {
  const InvoicesScreen({super.key});

  @override
  State<InvoicesScreen> createState() => _InvoicesScreenState();
}

class _InvoicesScreenState extends State<InvoicesScreen>
    with SingleTickerProviderStateMixin {
  final ApiService _api = ApiService();
  bool _loading = true;
  List _invoices = [];
  late TabController _tabController;

  final _dummy = [
    {
      'id': 1,
      'invoice_number': 'INV-2026-001',
      'tenant_name': 'Ramesh Kumar',
      'property_code': 'Room-101',
      'amount': 5000,
      'paid_amount': 0,
      'status': 'pending',
      'due_date': '2026-03-31',
      'invoice_date': '2026-03-01'
    },
    {
      'id': 2,
      'invoice_number': 'INV-2026-002',
      'tenant_name': 'Sunita Devi',
      'property_code': 'Flat-2B',
      'amount': 9500,
      'paid_amount': 9500,
      'status': 'paid',
      'due_date': '2026-03-25',
      'invoice_date': '2026-03-01'
    },
    {
      'id': 3,
      'invoice_number': 'INV-2026-003',
      'tenant_name': 'Ajay Singh',
      'property_code': 'Room-202',
      'amount': 4200,
      'paid_amount': 0,
      'status': 'overdue',
      'due_date': '2026-03-10',
      'invoice_date': '2026-03-01'
    },
    {
      'id': 4,
      'invoice_number': 'INV-2026-004',
      'tenant_name': 'Meena Gupta',
      'property_code': 'Shop-03',
      'amount': 14000,
      'paid_amount': 7000,
      'status': 'partial',
      'due_date': '2026-03-28',
      'invoice_date': '2026-03-01'
    },
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 4, vsync: this);
    _load();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final res = await _api.get(ApiConfig.ownerInvoices);
    if (!mounted) return;
    setState(() {
      _loading = false;
      _invoices = (res.success && res.data != null)
          ? (res.data is List ? res.data : [res.data])
          : _dummy;
    });
  }

  List _byStatus(String status) {
    if (status == 'all') return _invoices;
    return _invoices.where((i) => i['status'] == status).toList();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.primary,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: const Text('Invoices & Payments',
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
        bottom: TabBar(
          controller: _tabController,
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white60,
          indicatorColor: Colors.white,
          indicatorWeight: 3,
          labelStyle: const TextStyle(
              fontWeight: FontWeight.w600, fontSize: 12),
          tabs: const [
            Tab(text: 'All'),
            Tab(text: 'Pending'),
            Tab(text: 'Paid'),
            Tab(text: 'Overdue'),
          ],
        ),
      ),
      body: _loading
          ? const Center(
              child: CircularProgressIndicator(color: AppTheme.primary))
          : TabBarView(
              controller: _tabController,
              children: [
                _InvoiceList(
                    invoices: _byStatus('all'), onRefresh: _load),
                _InvoiceList(
                    invoices: _byStatus('pending'), onRefresh: _load),
                _InvoiceList(
                    invoices: _byStatus('paid'), onRefresh: _load),
                _InvoiceList(
                    invoices: _byStatus('overdue'), onRefresh: _load),
              ],
            ),
    );
  }
}

class _InvoiceList extends StatelessWidget {
  final List invoices;
  final VoidCallback onRefresh;

  const _InvoiceList(
      {required this.invoices, required this.onRefresh});

  @override
  Widget build(BuildContext context) {
    if (invoices.isEmpty) {
      return const EmptyState(
          icon: Icons.receipt_long_outlined,
          title: 'No Invoices',
          subtitle: 'No invoices found for this category');
    }
    return RefreshIndicator(
      onRefresh: () async => onRefresh(),
      child: ListView.builder(
        padding: const EdgeInsets.all(16),
        itemCount: invoices.length,
        itemBuilder: (_, i) => _InvoiceCard(invoice: invoices[i]),
      ),
    );
  }
}

class _InvoiceCard extends StatelessWidget {
  final Map invoice;

  const _InvoiceCard({required this.invoice});

  Color get _statusColor {
    switch (invoice['status']) {
      case 'paid':
        return AppTheme.statusPaid;
      case 'overdue':
        return AppTheme.statusOverdue;
      case 'partial':
        return AppTheme.accentOrange;
      default:
        return AppTheme.statusPending;
    }
  }

  @override
  Widget build(BuildContext context) {
    final amount =
        double.tryParse(invoice['amount'].toString()) ?? 0;
    final paid =
        double.tryParse(invoice['paid_amount'].toString()) ?? 0;
    final pending = amount - paid;
    final progress = amount > 0 ? paid / amount : 0.0;

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border(
          left: BorderSide(color: _statusColor, width: 4),
        ),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withOpacity(0.05),
              blurRadius: 8,
              offset: const Offset(0, 2))
        ],
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  invoice['invoice_number'] ?? '',
                  style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: AppTheme.textGrey,
                      letterSpacing: 0.5),
                ),
                StatusBadge(status: invoice['status'] ?? 'pending'),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              invoice['tenant_name'] ?? '',
              style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.textDark),
            ),
            const SizedBox(height: 2),
            Row(
              children: [
                const Icon(Icons.apartment_outlined,
                    size: 12, color: AppTheme.textGrey),
                const SizedBox(width: 4),
                Text(
                  '${invoice['property_code'] ?? ''} • Due: ${invoice['due_date'] ?? ''}',
                  style: const TextStyle(
                      fontSize: 12, color: AppTheme.textGrey),
                ),
              ],
            ),
            const SizedBox(height: 12),
            // Progress bar
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: progress.toDouble(),
                backgroundColor: AppTheme.divider,
                valueColor:
                    AlwaysStoppedAnimation(_statusColor),
                minHeight: 6,
              ),
            ),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Total: ₹${NumberFormat('#,##,###').format(amount)}',
                  style: const TextStyle(
                      fontSize: 12, color: AppTheme.textGrey),
                ),
                Text(
                  pending > 0
                      ? 'Due: ₹${NumberFormat('#,##,###').format(pending)}'
                      : 'Fully Paid ✓',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: pending > 0
                        ? _statusColor
                        : AppTheme.statusPaid,
                  ),
                ),
              ],
            ),
            if (invoice['status'] != 'paid') ...[
              const SizedBox(height: 10),
              SizedBox(
                height: 34,
                child: ElevatedButton.icon(
                  onPressed: () =>
                      _showPaymentDialog(context, invoice),
                  icon: const Icon(Icons.payment, size: 14),
                  label: const Text('Record Payment',
                      style: TextStyle(fontSize: 12)),
                  style: ElevatedButton.styleFrom(
                    minimumSize: const Size(double.infinity, 34),
                    backgroundColor: AppTheme.primary,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8)),
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  void _showPaymentDialog(BuildContext context, Map invoice) {
    final amtCtrl = TextEditingController(
        text: (double.tryParse(invoice['amount'].toString()) ?? 0 -
                double.tryParse(invoice['paid_amount'].toString()) ?? 0)
            .toString());
    String method = 'cash';
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => Container(
        decoration: const BoxDecoration(
          color: Colors.white,
          borderRadius:
              BorderRadius.vertical(top: Radius.circular(24)),
        ),
        padding: EdgeInsets.fromLTRB(
            20,
            20,
            20,
            MediaQuery.of(context).viewInsets.bottom + 20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.grey[300],
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'Record Payment',
              style: const TextStyle(
                  fontSize: 18, fontWeight: FontWeight.bold),
            ),
            Text(
              invoice['tenant_name'] ?? '',
              style: const TextStyle(
                  color: AppTheme.textGrey, fontSize: 13),
            ),
            const SizedBox(height: 20),
            TextField(
              controller: amtCtrl,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Amount (₹)',
                prefixIcon: Icon(Icons.currency_rupee,
                    color: AppTheme.primary),
              ),
            ),
            const SizedBox(height: 12),
            StatefulBuilder(builder: (ctx, setSt) {
              return Wrap(
                spacing: 8,
                children: ['cash', 'upi', 'bank_transfer', 'cheque']
                    .map((m) => ChoiceChip(
                          label: Text(m.toUpperCase()),
                          selected: method == m,
                          onSelected: (_) =>
                              setSt(() => method = m),
                          selectedColor:
                              AppTheme.primary.withOpacity(0.2),
                          labelStyle: TextStyle(
                            color: method == m
                                ? AppTheme.primary
                                : AppTheme.textGrey,
                            fontWeight: FontWeight.w600,
                            fontSize: 11,
                          ),
                        ))
                    .toList(),
              );
            }),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: () async {
                final api = ApiService();
                final res =
                    await api.post(ApiConfig.ownerInvoices, {
                  'invoice_id': invoice['id'],
                  'amount': amtCtrl.text,
                  'payment_method': method,
                  'payment_date': DateFormat('yyyy-MM-dd')
                      .format(DateTime.now()),
                  'remarks': '',
                });
                if (!context.mounted) return;
                Navigator.pop(context);
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(res.success
                        ? 'Payment recorded!'
                        : res.message),
                    backgroundColor: res.success
                        ? AppTheme.statusPaid
                        : AppTheme.accent,
                  ),
                );
              },
              style: ElevatedButton.styleFrom(
                  minimumSize:
                      const Size(double.infinity, 50)),
              child: const Text('Confirm Payment'),
            ),
          ],
        ),
      ),
    );
  }
}
