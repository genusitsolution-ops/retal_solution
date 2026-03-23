import 'package:url_launcher/url_launcher.dart';
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
  String? _error;
  List _invoices = [];
  late TabController _tabCtrl;

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: 4, vsync: this);
    _load();
  }

  @override
  void dispose() { _tabCtrl.dispose(); super.dispose(); }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    final res = await _api.get(ApiConfig.ownerInvoices);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (res.success && res.data != null) {
        _invoices = res.data is List ? res.data : (res.data['invoices'] ?? [res.data]);
      } else {
        _error = res.message.isNotEmpty ? res.message : 'Failed to load invoices';
      }
    });
  }

  List _byStatus(String s) => s == 'all' ? _invoices
      : _invoices.where((i) => i['status'] == s).toList();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.primary,
        leading: Navigator.canPop(context)
            ? IconButton(icon: const Icon(Icons.arrow_back, color: Colors.white), onPressed: () => Navigator.pop(context))
            : null,
        title: const Text('Invoices & Payments', style: TextStyle(color: Colors.white)),
        flexibleSpace: Container(decoration: const BoxDecoration(
          gradient: LinearGradient(colors: [AppTheme.primary, AppTheme.primaryLight],
              begin: Alignment.topLeft, end: Alignment.bottomRight),
        )),
        bottom: TabBar(
          controller: _tabCtrl,
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white60,
          indicatorColor: Colors.white,
          indicatorWeight: 3,
          labelStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12),
          tabs: const [Tab(text: 'All'), Tab(text: 'Pending'), Tab(text: 'Paid'), Tab(text: 'Overdue')],
        ),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: AppTheme.primary))
          : _error != null
              ? ErrorView(message: _error!, onRetry: _load)
              : TabBarView(
                  controller: _tabCtrl,
                  children: [
                    _InvList(invoices: _byStatus('all'), onRefresh: _load, onPay: _showPayment),
                    _InvList(invoices: _byStatus('pending'), onRefresh: _load, onPay: _showPayment),
                    _InvList(invoices: _byStatus('paid'), onRefresh: _load, onPay: _showPayment),
                    _InvList(invoices: _byStatus('overdue'), onRefresh: _load, onPay: _showPayment),
                  ],
                ),
    );
  }

  void _sendWhatsApp(Map inv) async {
    final phone = (inv['tenant_phone'] ?? inv['phone'] ?? '').toString();
    final invoiceNo = (inv['invoice_number'] ?? '').toString();
    final tenant = (inv['tenant_name'] ?? '').toString();
    final property = (inv['property_code'] ?? '').toString();
    final dueDate = (inv['due_date'] ?? '').toString();
    final totalAmt = double.tryParse((inv['amount'] ?? inv['total_amount'] ?? '0').toString()) ?? 0.0;
    final paidAmt = double.tryParse((inv['paid_amount'] ?? '0').toString()) ?? 0.0;
    final pendingAmt = (totalAmt - paidAmt).clamp(0.0, double.infinity);

<<<<<<< HEAD
    final statusLine = pendingAmt > 0
        ? 'Pending Amount: Rs.${pendingAmt.toStringAsFixed(0)}'
        : 'Status: FULLY PAID';

    final msgText = 'Dear $tenant,'
        '\n\nInvoice: $invoiceNo'
        '\nProperty: $property'
        '\nTotal: Rs.${totalAmt.toStringAsFixed(0)}'
        '\nPaid: Rs.${paidAmt.toStringAsFixed(0)}'
        '\n$statusLine'
        '\nDue Date: $dueDate'
        '\n\nThank you. - PRMS Management';
=======
'
      'Invoice: *$invoiceNo*
'
      'Property: *$property*
'
      'Total Amount: *₹$amount*
'
      'Amount Paid: *₹$paid*
'
'${pending > 0 ? "Pending: *Rs.${pending.toStringAsFixed(0)}*\n" : "Status: *FULLY PAID*\n"}'
"}'
      'Due Date: *$dueDate*
>>>>>>> dafcc648f7c323095c2c587f087a31a60c505c53

    if (phone.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Phone number not available')));
      return;
    }
    final url = Uri.parse('https://wa.me/91$phone?text=${Uri.encodeComponent(msgText)}');
    if (await canLaunchUrl(url)) {
      await launchUrl(url, mode: LaunchMode.externalApplication);
    } else {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('WhatsApp not available')));
    }
  }

  
  void _showPayment(Map inv) {
    // Get total amount - try multiple field names
    final totalAmt = double.tryParse(
      (inv['amount'] ?? inv['total_amount'] ?? inv['bill_amount'] ?? inv['rent_amount'] ?? '0').toString()) ?? 0.0;
    final paidAmt = double.tryParse(
      (inv['paid_amount'] ?? inv['amount_paid'] ?? '0').toString()) ?? 0.0;
    // Pending = total - paid, but minimum 0
    final pendingAmt = (totalAmt - paidAmt).clamp(0.0, totalAmt > 0 ? totalAmt : double.infinity);
    final amtCtrl = TextEditingController(text: pendingAmt > 0 ? pendingAmt.toStringAsFixed(0) : totalAmt.toStringAsFixed(0));
    String method = 'cash';
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSt) => Container(
          decoration: const BoxDecoration(color: Colors.white,
              borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
          padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(ctx).viewInsets.bottom + 20),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            Center(child: Container(width: 40, height: 4,
                decoration: BoxDecoration(color: Colors.grey[300], borderRadius: BorderRadius.circular(2)))),
            const SizedBox(height: 16),
            const Text('Record Payment', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            Text(inv['tenant_name'] ?? '', style: const TextStyle(color: AppTheme.textGrey, fontSize: 13)),
            const SizedBox(height: 12),
            // Show balance info
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppTheme.statusPending.withOpacity(0.08),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: AppTheme.statusPending.withOpacity(0.3)),
              ),
              child: Row(children: [
                const Icon(Icons.account_balance_wallet, color: AppTheme.statusPending, size: 18),
                const SizedBox(width: 8),
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('Invoice: ${inv['invoice_number'] ?? ''}',
                      style: const TextStyle(fontSize: 11, color: AppTheme.textGrey)),
                  Text(
                    'Pending: ₹${pendingAmt > 0 ? NumberFormat('#,##,###').format(pendingAmt) : NumberFormat('#,##,###').format(totalAmt)}',
                    style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppTheme.statusPending),
                  ),
                ]),
              ]),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: amtCtrl,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(
                labelText: 'Amount to Pay (₹)',
                hintText: 'Enter amount ≤ pending balance',
                prefixIcon: Icon(Icons.currency_rupee, color: AppTheme.primary),
              ),
            ),
            const SizedBox(height: 12),
            const Text('Payment Mode', style: TextStyle(fontSize: 13, color: AppTheme.textGrey)),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              children: ['cash', 'upi', 'bank_transfer', 'cheque'].map((m) => ChoiceChip(
                label: Text(m.replaceAll('_', ' ').toUpperCase()),
                selected: method == m,
                onSelected: (_) => setSt(() => method = m),
                selectedColor: AppTheme.primary.withOpacity(0.2),
                labelStyle: TextStyle(
                  color: method == m ? AppTheme.primary : AppTheme.textGrey,
                  fontWeight: FontWeight.w600, fontSize: 11,
                ),
              )).toList(),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: () async {
                final api = ApiService();
                final res = await api.post(ApiConfig.ownerInvoices, {
                  'invoice_id': inv['id'],
                  'amount': amtCtrl.text,
                  'payment_method': method,
                  'payment_date': DateFormat('yyyy-MM-dd').format(DateTime.now()),
                  'remarks': '',
                });
                if (!context.mounted) return;
                Navigator.pop(context);
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                  content: Text(res.success ? 'Payment recorded!' : res.message),
                  backgroundColor: res.success ? AppTheme.statusPaid : AppTheme.accent,
                ));
                if (res.success) _load();
              },
              style: ElevatedButton.styleFrom(minimumSize: const Size(double.infinity, 50)),
              child: const Text('Confirm Payment'),
            ),
          ]),
        ),
      ),
    );
  }
}

class _InvList extends StatelessWidget {
  final List invoices;
  final VoidCallback onRefresh;
  final Function(Map) onPay;
  final Function(Map) onWhatsApp;
  const _InvList({required this.invoices, required this.onRefresh, required this.onPay, required this.onWhatsApp});

  @override
  Widget build(BuildContext context) {
    if (invoices.isEmpty) {
      return const EmptyState(icon: Icons.receipt_long_outlined, title: 'No Invoices');
    }
    return RefreshIndicator(
      onRefresh: () async => onRefresh(),
      child: ListView.builder(
        padding: const EdgeInsets.all(16),
        itemCount: invoices.length,
        itemBuilder: (_, i) => _InvCard(invoice: invoices[i], onPay: onPay, onWhatsApp: onWhatsApp),
      ),
    );
  }
}

class _InvCard extends StatelessWidget {
  final Map invoice;
  final Function(Map) onPay;
  final Function(Map) onWhatsApp;
  const _InvCard({required this.invoice, required this.onPay, required this.onWhatsApp});

  Color get _color {
    switch (invoice['status']) {
      case 'paid': return AppTheme.statusPaid;
      case 'overdue': return AppTheme.statusOverdue;
      case 'partial': return AppTheme.accentOrange;
      default: return AppTheme.statusPending;
    }
  }

  @override
  Widget build(BuildContext context) {
    // Handle multiple possible field names from API
    final amount = double.tryParse(
      (invoice['amount'] ?? invoice['total_amount'] ?? invoice['bill_amount'] ?? invoice['rent_amount'] ?? '0').toString()) ?? 0.0;
    final paid = double.tryParse(
      (invoice['paid_amount'] ?? invoice['amount_paid'] ?? '0').toString()) ?? 0.0;
    final pending = (amount - paid).clamp(0.0, double.infinity);
    final progress = amount > 0 ? (paid / amount).clamp(0.0, 1.0) : 0.0;

    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: Border(left: BorderSide(color: _color, width: 4)),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
            Text(invoice['invoice_number'] ?? '',
                style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600,
                    color: AppTheme.textGrey, letterSpacing: 0.5)),
            StatusBadge(status: invoice['status'] ?? 'pending'),
          ]),
          const SizedBox(height: 8),
          Text(invoice['tenant_name'] ?? '',
              style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppTheme.textDark)),
          const SizedBox(height: 2),
          Row(children: [
            const Icon(Icons.apartment_outlined, size: 12, color: AppTheme.textGrey),
            const SizedBox(width: 4),
            Text('${invoice['property_code'] ?? ''} • Due: ${invoice['due_date'] ?? ''}',
                style: const TextStyle(fontSize: 12, color: AppTheme.textGrey)),
          ]),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: progress,
              backgroundColor: AppTheme.divider,
              valueColor: AlwaysStoppedAnimation(_color),
              minHeight: 6,
            ),
          ),
          const SizedBox(height: 8),
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
            Text('Total: ₹${NumberFormat('#,##,###').format(amount)}',
                style: const TextStyle(fontSize: 12, color: AppTheme.textGrey)),
            Text(
              pending > 0 ? 'Due: ₹${NumberFormat('#,##,###').format(pending)}' : 'Fully Paid ✓',
              style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600,
                  color: pending > 0 ? _color : AppTheme.statusPaid),
            ),
          ]),
          const SizedBox(height: 10),
          Row(children: [
            if (invoice['status'] != 'paid')
              Expanded(child: SizedBox(
                height: 36,
                child: ElevatedButton.icon(
                  onPressed: () => onPay(invoice),
                  icon: const Icon(Icons.payment, size: 14),
                  label: const Text('Record Payment', style: TextStyle(fontSize: 12)),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.primary,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                ),
              )),
            if (invoice['status'] != 'paid') const SizedBox(width: 8),
            Expanded(child: SizedBox(
              height: 36,
              child: OutlinedButton.icon(
                onPressed: () => onWhatsApp(invoice),
                icon: const Icon(Icons.chat, size: 14, color: Color(0xFF25D366)),
                label: const Text('WhatsApp', style: TextStyle(fontSize: 12, color: Color(0xFF25D366))),
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: Color(0xFF25D366)),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
              ),
            )),
          ]),
        ]),
      ),
    );
  }
}
