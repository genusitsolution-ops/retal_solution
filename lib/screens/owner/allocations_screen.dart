// lib/screens/owner/allocations_screen.dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class AllocationsScreen extends StatefulWidget {
  const AllocationsScreen({super.key});
  @override
  State<AllocationsScreen> createState() => _AllocationsScreenState();
}

class _AllocationsScreenState extends State<AllocationsScreen> {
  final ApiService _api = ApiService();
  bool _loading = true;
  String? _error;
  List _allocations = [];

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    final res = await _api.get(ApiConfig.ownerAllocations);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (res.success && res.data != null) {
        _allocations = res.data is List ? res.data : (res.data['allocations'] ?? []);
      } else {
        _error = res.message.isNotEmpty ? res.message : 'Failed to load allocations';
      }
    });
  }

  Future<void> _closeAllocation(dynamic id) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Close Allocation?'),
        content: const Text('This will mark the tenant as vacated.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () => Navigator.pop(context, true),
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accent),
            child: const Text('Close', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );
    if (confirm != true) return;
    final res = await _api.put('${ApiConfig.ownerAllocations}?id=$id',
        {'end_date': DateFormat('yyyy-MM-dd').format(DateTime.now()), 'status': 'closed'});
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(res.success ? 'Allocation closed!' : res.message),
      backgroundColor: res.success ? AppTheme.statusPaid : AppTheme.accent,
    ));
    if (res.success) _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.primary,
        title: const Text('Property Allocations', style: TextStyle(color: Colors.white)),
        flexibleSpace: Container(decoration: const BoxDecoration(
          gradient: LinearGradient(colors: [AppTheme.primary, AppTheme.primaryLight],
              begin: Alignment.topLeft, end: Alignment.bottomRight),
        )),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showForm(),
        backgroundColor: AppTheme.primary,
        icon: const Icon(Icons.add_home, color: Colors.white),
        label: const Text('New Allocation', style: TextStyle(color: Colors.white)),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: AppTheme.primary))
          : _error != null
              ? ErrorView(message: _error!, onRetry: _load)
              : _allocations.isEmpty
                  ? EmptyState(icon: Icons.home_work_outlined, title: 'No Allocations',
                      actionLabel: 'Create Allocation', onAction: () => _showForm())
                  : RefreshIndicator(
                      onRefresh: _load,
                      child: ListView.builder(
                        padding: const EdgeInsets.fromLTRB(16, 16, 16, 80),
                        itemCount: _allocations.length,
                        itemBuilder: (_, i) {
                          final a = _allocations[i];
                          final isActive = a['status'] == 'active';
                          return Container(
                            margin: const EdgeInsets.only(bottom: 10),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(14),
                              border: Border(left: BorderSide(
                                color: isActive ? AppTheme.statusPaid : AppTheme.textGrey,
                                width: 4,
                              )),
                              boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05),
                                  blurRadius: 8, offset: const Offset(0, 2))],
                            ),
                            child: Padding(
                              padding: const EdgeInsets.all(14),
                              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                                  Text(a['tenant_name'] ?? '', style: const TextStyle(
                                      fontSize: 15, fontWeight: FontWeight.bold, color: AppTheme.textDark)),
                                  StatusBadge(status: a['status'] ?? 'active'),
                                ]),
                                const SizedBox(height: 6),
                                Row(children: [
                                  const Icon(Icons.apartment, size: 14, color: AppTheme.textGrey),
                                  const SizedBox(width: 4),
                                  Text(a['property_code'] ?? '', style: const TextStyle(fontSize: 13, color: AppTheme.textGrey)),
                                  const SizedBox(width: 16),
                                  const Icon(Icons.currency_rupee, size: 14, color: AppTheme.primary),
                                  Text('${a['rent_amount'] ?? a['base_rent'] ?? 0}/mo',
                                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: AppTheme.primary)),
                                ]),
                                const SizedBox(height: 4),
                                Text('Start: ${a['start_date'] ?? ''} ${a['end_date'] != null ? "• End: ${a['end_date']}" : ""}',
                                    style: const TextStyle(fontSize: 12, color: AppTheme.textGrey)),
                                if (isActive) ...[
                                  const SizedBox(height: 10),
                                  Row(mainAxisAlignment: MainAxisAlignment.end, children: [
                                    OutlinedButton.icon(
                                      onPressed: () => _closeAllocation(a['id']),
                                      icon: const Icon(Icons.exit_to_app, size: 14),
                                      label: const Text('Close Allocation', style: TextStyle(fontSize: 12)),
                                      style: OutlinedButton.styleFrom(
                                        foregroundColor: AppTheme.accent,
                                        side: const BorderSide(color: AppTheme.accent),
                                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                      ),
                                    ),
                                  ]),
                                ],
                              ]),
                            ),
                          );
                        },
                      ),
                    ),
    );
  }

  void _showForm() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _AllocationForm(onSaved: _load),
    );
  }
}

class _AllocationForm extends StatefulWidget {
  final VoidCallback onSaved;
  const _AllocationForm({required this.onSaved});
  @override
  State<_AllocationForm> createState() => _AllocationFormState();
}

class _AllocationFormState extends State<_AllocationForm> {
  final ApiService _api = ApiService();
  bool _loading = true;
  List _properties = [];
  List _tenants = [];
  Map? _selectedProperty;
  Map? _selectedTenant;
  final _rentCtrl = TextEditingController();
  final _depositCtrl = TextEditingController();
  DateTime _startDate = DateTime.now();
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    final pRes = await _api.get(ApiConfig.ownerProperties);
    final tRes = await _api.get(ApiConfig.ownerTenants);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (pRes.success && pRes.data != null) {
        final all = pRes.data is List ? pRes.data : (pRes.data['properties'] ?? []);
        _properties = all.where((p) => p['status'] != 'occupied').toList();
      }
      if (tRes.success && tRes.data != null) {
        _tenants = tRes.data is List ? tRes.data : (tRes.data['tenants'] ?? []);
      }
    });
  }

  Future<void> _save() async {
    if (_selectedProperty == null || _selectedTenant == null) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Please select property and tenant')));
      return;
    }
    setState(() => _saving = true);
    final res = await _api.post(ApiConfig.ownerAllocations, {
      'property_id': _selectedProperty!['id'],
      'tenant_id': _selectedTenant!['id'],
      'start_date': DateFormat('yyyy-MM-dd').format(_startDate),
      'rent_amount': _rentCtrl.text.isEmpty ? (_selectedProperty!['base_rent'] ?? 0).toString() : _rentCtrl.text,
      'deposit_amount': _depositCtrl.text,
    });
    if (!mounted) return;
    setState(() => _saving = false);
    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Text(res.success ? 'Allocation created!' : res.message),
      backgroundColor: res.success ? AppTheme.statusPaid : AppTheme.accent,
    ));
    if (res.success) widget.onSaved();
  }

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
          const Text('New Allocation', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 20),
          if (_loading)
            const Center(child: CircularProgressIndicator())
          else ...[
            DropdownButtonFormField<Map>(
              value: _selectedProperty,
              decoration: const InputDecoration(labelText: 'Select Property (Vacant)',
                  prefixIcon: Icon(Icons.apartment, color: AppTheme.primary)),
              items: _properties.map((p) => DropdownMenuItem<Map>(
                value: p as Map,
                child: Text('${p['property_code']} - ${p['city'] ?? ''} (₹${p['base_rent']}/mo)'),
              )).toList(),
              onChanged: (v) {
                setState(() {
                  _selectedProperty = v;
                  _rentCtrl.text = v?['base_rent']?.toString() ?? '';
                  _depositCtrl.text = v?['deposit_amount']?.toString() ?? '';
                });
              },
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<Map>(
              value: _selectedTenant,
              decoration: const InputDecoration(labelText: 'Select Tenant',
                  prefixIcon: Icon(Icons.person, color: AppTheme.primary)),
              items: _tenants.map((t) => DropdownMenuItem<Map>(
                value: t as Map,
                child: Text('${t['full_name']} (${t['phone'] ?? ''})'),
              )).toList(),
              onChanged: (v) => setState(() => _selectedTenant = v),
            ),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(child: TextFormField(
                controller: _rentCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Rent Amount',
                    prefixIcon: Icon(Icons.currency_rupee, color: AppTheme.primary)),
              )),
              const SizedBox(width: 12),
              Expanded(child: TextFormField(
                controller: _depositCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Deposit',
                    prefixIcon: Icon(Icons.savings, color: AppTheme.primary)),
              )),
            ]),
            const SizedBox(height: 12),
            GestureDetector(
              onTap: () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: _startDate,
                  firstDate: DateTime(2020),
                  lastDate: DateTime(2030),
                );
                if (picked != null) setState(() => _startDate = picked);
              },
              child: Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: const Color(0xFFF0F2F5),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(children: [
                  const Icon(Icons.calendar_today, color: AppTheme.primary, size: 18),
                  const SizedBox(width: 10),
                  Text('Start Date: ${DateFormat('dd MMM yyyy').format(_startDate)}',
                      style: const TextStyle(fontSize: 14)),
                ]),
              ),
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: _saving ? null : _save,
              style: ElevatedButton.styleFrom(minimumSize: const Size(double.infinity, 50)),
              child: _saving
                  ? const CircularProgressIndicator(color: Colors.white, strokeWidth: 2)
                  : const Text('Create Allocation'),
            ),
          ],
        ]),
      ),
    );
  }
}
