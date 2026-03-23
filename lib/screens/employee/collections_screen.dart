// lib/screens/employee/collections_screen.dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class CollectionsScreen extends StatefulWidget {
  const CollectionsScreen({super.key});
  @override
  State<CollectionsScreen> createState() => _CollectionsScreenState();
}

class _CollectionsScreenState extends State<CollectionsScreen> {
  final ApiService _api = ApiService();
  bool _loading = true;
  String? _error;
  List _collections = [];
  List _tenants = [];

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    final now = DateTime.now();
    final from = DateFormat('yyyy-MM-dd').format(now.subtract(const Duration(days: 30)));
    final to = DateFormat('yyyy-MM-dd').format(now);
    final res = await _api.get('${ApiConfig.empCollections}?from=$from&to=$to');
    final tenantsRes = await _api.get(ApiConfig.empTenants);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (res.success && res.data != null) {
        _collections = res.data is List ? res.data : (res.data['collections'] ?? []);
      } else {
        _error = res.message.isNotEmpty ? res.message : 'Failed to load collections';
      }
      if (tenantsRes.success && tenantsRes.data != null) {
        _tenants = tenantsRes.data is List ? tenantsRes.data : (tenantsRes.data['tenants'] ?? []);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.accentOrange,
        title: const Text('Collections', style: TextStyle(color: Colors.white)),
        flexibleSpace: Container(decoration: const BoxDecoration(
          gradient: LinearGradient(colors: [Color(0xFFE65100), Color(0xFFFF6D00)],
              begin: Alignment.topLeft, end: Alignment.bottomRight),
        )),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showAddSheet(),
        backgroundColor: AppTheme.accentOrange,
        icon: const Icon(Icons.add, color: Colors.white),
        label: const Text('Record Collection', style: TextStyle(color: Colors.white)),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: AppTheme.accentOrange))
          : _error != null
              ? ErrorView(message: _error!, onRetry: _load)
              : _collections.isEmpty
                  ? const EmptyState(icon: Icons.receipt_long_outlined, title: 'No Collections',
                      subtitle: 'No collections in last 30 days')
                  : RefreshIndicator(
                      onRefresh: _load,
                      child: ListView.builder(
                        padding: const EdgeInsets.fromLTRB(16, 16, 16, 80),
                        itemCount: _collections.length,
                        itemBuilder: (_, i) => _CollCard(c: _collections[i]),
                      ),
                    ),
    );
  }

  void _showAddSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _AddSheet(tenants: _tenants, onAdded: _load),
    );
  }
}

class _CollCard extends StatelessWidget {
  final Map c;
  const _CollCard({required this.c});

  IconData _icon(String? mode) {
    switch ((mode ?? '').toLowerCase()) {
      case 'upi': return Icons.phone_android;
      case 'bank_transfer': return Icons.account_balance;
      case 'cheque': return Icons.receipt;
      default: return Icons.payments;
    }
  }

  @override
  Widget build(BuildContext context) {
    final amount = double.tryParse(c['amount']?.toString() ?? '0') ?? 0.0;
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        border: const Border(left: BorderSide(color: AppTheme.statusPaid, width: 4)),
        boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 8, offset: const Offset(0, 2))],
      ),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Row(children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(color: AppTheme.statusPaid.withOpacity(0.1), borderRadius: BorderRadius.circular(10)),
            child: Icon(_icon(c['payment_mode']), color: AppTheme.statusPaid, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(c['tenant_name'] ?? '',
                style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppTheme.textDark)),
            const SizedBox(height: 2),
            Text('${c['property_code'] ?? ''} • ${(c['payment_mode'] ?? '').toUpperCase()} • ${c['collection_date'] ?? ''}',
                style: const TextStyle(fontSize: 11, color: AppTheme.textGrey)),
            if (c['receipt_number'] != null)
              Text(c['receipt_number'], style: const TextStyle(fontSize: 11, color: AppTheme.textGrey)),
          ])),
          Text('₹${NumberFormat('#,##,###').format(amount)}',
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.statusPaid)),
        ]),
      ),
    );
  }
}

class _AddSheet extends StatefulWidget {
  final List tenants;
  final VoidCallback onAdded;
  const _AddSheet({required this.tenants, required this.onAdded});
  @override
  State<_AddSheet> createState() => _AddSheetState();
}

class _AddSheetState extends State<_AddSheet> {
  final _amtCtrl = TextEditingController();
  final _remarksCtrl = TextEditingController();
  String _mode = 'cash';
  bool _saving = false;
  Map? _selectedTenant;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(color: Colors.white,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(context).viewInsets.bottom + 20),
      child: SingleChildScrollView(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
          Center(child: Container(width: 40, height: 4,
              decoration: BoxDecoration(color: Colors.grey[300], borderRadius: BorderRadius.circular(2)))),
          const SizedBox(height: 16),
          const Text('Record Collection', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 20),
          if (widget.tenants.isEmpty)
            const Text('No tenants assigned to you', style: TextStyle(color: AppTheme.textGrey))
          else
            DropdownButtonFormField<Map>(
              value: _selectedTenant,
              decoration: const InputDecoration(
                labelText: 'Select Tenant',
                prefixIcon: Icon(Icons.person_outline, color: AppTheme.accentOrange),
              ),
              items: widget.tenants.map((t) => DropdownMenuItem<Map>(
                value: t as Map,
                child: Text('${t['full_name']} - ${t['property_code'] ?? ''}'),
              )).toList(),
              onChanged: (v) {
                setState(() {
                  _selectedTenant = v;
                  if (v != null) {
                    _amtCtrl.text = (v['pending'] ?? v['base_rent'] ?? '').toString();
                  }
                });
              },
            ),
          const SizedBox(height: 12),
          TextField(
            controller: _amtCtrl,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(
              labelText: 'Amount (₹)',
              prefixIcon: Icon(Icons.currency_rupee, color: AppTheme.accentOrange),
            ),
          ),
          const SizedBox(height: 12),
          const Text('Payment Mode', style: TextStyle(fontSize: 13, color: AppTheme.textGrey)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: ['cash', 'upi', 'bank_transfer', 'cheque'].map((m) => ChoiceChip(
              label: Text(m.replaceAll('_', ' ').toUpperCase()),
              selected: _mode == m,
              onSelected: (_) => setState(() => _mode = m),
              selectedColor: AppTheme.accentOrange.withOpacity(0.2),
              labelStyle: TextStyle(
                color: _mode == m ? AppTheme.accentOrange : AppTheme.textGrey,
                fontWeight: FontWeight.w600, fontSize: 11,
              ),
            )).toList(),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _remarksCtrl,
            decoration: const InputDecoration(
              labelText: 'Remarks (optional)',
              prefixIcon: Icon(Icons.note_outlined, color: AppTheme.accentOrange),
            ),
          ),
          const SizedBox(height: 20),
          ElevatedButton(
            onPressed: _saving ? null : _save,
            style: ElevatedButton.styleFrom(
              minimumSize: const Size(double.infinity, 50),
              backgroundColor: AppTheme.accentOrange,
            ),
            child: _saving
                ? const CircularProgressIndicator(color: Colors.white, strokeWidth: 2)
                : const Text('Submit Collection'),
          ),
        ]),
      ),
    );
  }

  Future<void> _save() async {
    if (_selectedTenant == null || _amtCtrl.text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please select tenant and enter amount')));
      return;
    }
    setState(() => _saving = true);
    final api = ApiService();
    final res = await api.post(ApiConfig.empCollections, {
      'property_id': _selectedTenant!['property_id'] ?? _selectedTenant!['id'],
      'tenant_id': _selectedTenant!['id'],
      'invoice_id': _selectedTenant!['invoice_id'],
      'amount': _amtCtrl.text,
      'payment_mode': _mode,
      'collection_date': DateFormat('yyyy-MM-dd').format(DateTime.now()),
      'remarks': _remarksCtrl.text,
    });
    if (!mounted) return;
    setState(() => _saving = false);
    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(res.success ? 'Collection recorded!' : res.message),
      backgroundColor: res.success ? AppTheme.statusPaid : AppTheme.accent,
    ));
    if (res.success) widget.onAdded();
  }
}
