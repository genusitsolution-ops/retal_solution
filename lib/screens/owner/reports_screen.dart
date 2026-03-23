// lib/screens/owner/reports_screen.dart
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../config/constants.dart';
import '../../services/api_service.dart';
import '../../widgets/common_widgets.dart';

class ReportsScreen extends StatefulWidget {
  const ReportsScreen({super.key});
  @override
  State<ReportsScreen> createState() => _ReportsScreenState();
}

class _ReportsScreenState extends State<ReportsScreen>
    with SingleTickerProviderStateMixin {
  final ApiService _api = ApiService();
  late TabController _tabCtrl;
  bool _loading = false;
  String? _error;
  List _reportData = [];
  DateTime _from = DateTime.now().subtract(const Duration(days: 30));
  DateTime _to = DateTime.now();

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: 3, vsync: this);
    _tabCtrl.addListener(() { if (!_tabCtrl.indexIsChanging) _loadReport(); });
    _loadReport();
  }

  @override
  void dispose() { _tabCtrl.dispose(); super.dispose(); }

  String get _reportType {
    switch (_tabCtrl.index) {
      case 0: return 'collections';
      case 1: return 'revenue';
      case 2: return 'pending';
      default: return 'collections';
    }
  }

  Future<void> _loadReport() async {
    setState(() { _loading = true; _error = null; _reportData = []; });
    final fromStr = DateFormat('yyyy-MM-dd').format(_from);
    final toStr = DateFormat('yyyy-MM-dd').format(_to);
    final res = await _api.get(
        '${ApiConfig.ownerReports}?type=$_reportType&from=$fromStr&to=$toStr');
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (res.success && res.data != null) {
        _reportData = res.data is List ? res.data : (res.data['data'] ?? res.data['report'] ?? []);
      } else {
        _error = res.message.isNotEmpty ? res.message : 'Failed to load report';
      }
    });
  }

  Future<void> _pickDate(bool isFrom) async {
    final picked = await showDatePicker(
      context: context,
      initialDate: isFrom ? _from : _to,
      firstDate: DateTime(2020),
      lastDate: DateTime(2030),
    );
    if (picked != null) {
      setState(() { if (isFrom) _from = picked; else _to = picked; });
      _loadReport();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      appBar: AppBar(
        backgroundColor: AppTheme.primary,
        title: const Text('Reports', style: TextStyle(color: Colors.white)),
        flexibleSpace: Container(decoration: const BoxDecoration(
          gradient: LinearGradient(colors: [AppTheme.primary, AppTheme.primaryLight],
              begin: Alignment.topLeft, end: Alignment.bottomRight),
        )),
        bottom: TabBar(
          controller: _tabCtrl,
          labelColor: Colors.white,
          unselectedLabelColor: Colors.white60,
          indicatorColor: Colors.white,
          labelStyle: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12),
          tabs: const [Tab(text: 'Collections'), Tab(text: 'Revenue'), Tab(text: 'Pending')],
        ),
      ),
      body: Column(children: [
        // Date filter
        Container(
          padding: const EdgeInsets.all(16),
          color: Colors.white,
          child: Row(children: [
            Expanded(child: GestureDetector(
              onTap: () => _pickDate(true),
              child: Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppTheme.background,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppTheme.divider),
                ),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const Text('From', style: TextStyle(fontSize: 11, color: AppTheme.textGrey)),
                  Text(DateFormat('d MMM yyyy').format(_from),
                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                ]),
              ),
            )),
            const SizedBox(width: 12),
            Expanded(child: GestureDetector(
              onTap: () => _pickDate(false),
              child: Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppTheme.background,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppTheme.divider),
                ),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const Text('To', style: TextStyle(fontSize: 11, color: AppTheme.textGrey)),
                  Text(DateFormat('d MMM yyyy').format(_to),
                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                ]),
              ),
            )),
            const SizedBox(width: 12),
            ElevatedButton(
              onPressed: _loadReport,
              style: ElevatedButton.styleFrom(
                  minimumSize: const Size(60, 44),
                  padding: const EdgeInsets.symmetric(horizontal: 12)),
              child: const Icon(Icons.filter_alt, size: 20),
            ),
          ]),
        ),
        Expanded(
          child: _loading
              ? const Center(child: CircularProgressIndicator(color: AppTheme.primary))
              : _error != null
                  ? ErrorView(message: _error!, onRetry: _loadReport)
                  : _reportData.isEmpty
                      ? const EmptyState(icon: Icons.bar_chart, title: 'No Data',
                          subtitle: 'No report data for selected period')
                      : TabBarView(
                          controller: _tabCtrl,
                          children: [
                            _buildTable(_reportType == 'collections'),
                            _buildTable(false),
                            _buildTable(false),
                          ],
                        ),
        ),
      ]),
    );
  }

  Widget _buildTable(bool showAgent) {
    if (_reportData.isEmpty) {
      return const EmptyState(icon: Icons.bar_chart, title: 'No Data');
    }
    // Calculate total
    double total = 0;
    for (var item in _reportData) {
      total += double.tryParse(item['amount']?.toString() ?? item['total']?.toString() ?? '0') ?? 0.0;
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(children: [
        // Summary card
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [AppTheme.primary, AppTheme.primaryLight],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(14),
          ),
          child: Row(children: [
            const Icon(Icons.summarize, color: Colors.white, size: 28),
            const SizedBox(width: 12),
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              const Text('Total', style: TextStyle(color: Colors.white70, fontSize: 12)),
              Text('₹${NumberFormat('#,##,###').format(total)}',
                  style: const TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.bold)),
              Text('${_reportData.length} records', style: const TextStyle(color: Colors.white60, fontSize: 12)),
            ]),
          ]),
        ),
        const SizedBox(height: 16),
        // Data list
        ..._reportData.map((item) => Container(
          margin: const EdgeInsets.only(bottom: 8),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
            boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 6, offset: const Offset(0, 2))],
          ),
          child: Row(children: [
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(item['employee_name'] ?? item['month'] ?? item['tenant_name'] ?? item['invoice_number'] ?? '',
                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppTheme.textDark)),
              if (item['property_code'] != null)
                Text(item['property_code'], style: const TextStyle(fontSize: 12, color: AppTheme.textGrey)),
              if (item['date'] != null || item['due_date'] != null)
                Text(item['date'] ?? item['due_date'] ?? '',
                    style: const TextStyle(fontSize: 11, color: AppTheme.textGrey)),
            ])),
            Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
              Text(
                '₹${NumberFormat('#,##,###').format(double.tryParse(item['amount']?.toString() ?? item['total']?.toString() ?? '0') ?? 0.0)}',
                style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppTheme.primary),
              ),
              if (item['status'] != null) StatusBadge(status: item['status']),
            ]),
          ]),
        )),
      ]),
    );
  }
}
