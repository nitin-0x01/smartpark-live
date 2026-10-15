import React, { useState } from 'react';
import { DatabaseState } from '../mockDb';
import { TrendingUp, DollarSign, Download, Calendar, PieChart, CreditCard, Car, Bike, Zap, CheckCircle2 } from 'lucide-react';

interface AdminRevenueAnalyticsProps {
  dbState: DatabaseState;
}

export const AdminRevenueAnalytics: React.FC<AdminRevenueAnalyticsProps> = ({ dbState }) => {
  const [timeRange, setTimeRange] = useState<'DAILY' | 'WEEKLY' | 'MONTHLY'>('DAILY');

  const payments = dbState.payments;
  const totalRevenue = payments.reduce((sum, p) => sum + p.amount, 0);

  // Vehicle type breakdown
  const revenueByVehicleType = payments.reduce((acc, p) => {
    const tkt = dbState.tickets.find(t => t.ticket_id === p.ticket_id);
    const typeName = tkt?.type_name || (p.reg_no.includes('EV') ? 'EV Car' : 'Car');
    acc[typeName] = (acc[typeName] || 0) + p.amount;
    return acc;
  }, {} as Record<string, number>);

  // Payment mode breakdown
  const revenueByMode = payments.reduce((acc, p) => {
    acc[p.mode] = (acc[p.mode] || 0) + p.amount;
    return acc;
  }, {} as Record<string, number>);

  // Export to CSV
  const handleExportCSV = () => {
    const headers = ['Payment ID', 'Ticket ID', 'Vehicle Reg', 'Amount (INR)', 'Payment Mode', 'Date & Time', 'Transaction Ref'];
    const rows = payments.map(p => [
      p.payment_id,
      p.ticket_id || 'EV-DIRECT',
      `"${p.reg_no}"`,
      p.amount.toFixed(2),
      p.mode,
      `"${new Date(p.pay_time).toLocaleString()}"`,
      `"${p.transaction_ref}"`
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map(e => e.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `smartpark_revenue_report_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="space-y-6">
      {/* Analytics Top Control */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
            <TrendingUp className="w-5 h-5 text-blue-600" />
            <span>Revenue Analytics &amp; Financial Audits</span>
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Real-time financial aggregation across parking tickets, advance reservations &amp; EV charging
          </p>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex bg-slate-100 p-1 rounded-xl text-xs font-semibold">
            {(['DAILY', 'WEEKLY', 'MONTHLY'] as const).map(range => (
              <button
                key={range}
                onClick={() => setTimeRange(range)}
                className={`px-3 py-1.5 rounded-lg transition ${
                  timeRange === range ? 'bg-white text-blue-700 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                {range}
              </button>
            ))}
          </div>

          <button
            onClick={handleExportCSV}
            className="flex items-center gap-1.5 bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold py-2 px-3.5 rounded-xl transition shadow-xs"
          >
            <Download className="w-4 h-4" />
            <span>Export CSV</span>
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-gradient-to-br from-blue-900 to-indigo-900 text-white p-5 rounded-2xl shadow-sm">
          <span className="text-xs text-blue-200 block font-medium uppercase tracking-wider">Gross Total Collection</span>
          <span className="text-3xl font-black mt-2 block">₹{totalRevenue.toFixed(2)}</span>
          <p className="text-xs text-blue-200 mt-2 flex items-center gap-1">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
            Across {payments.length} verified transactions
          </p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
          <span className="text-xs text-slate-400 block font-medium uppercase tracking-wider">Average Ticket Fare</span>
          <span className="text-3xl font-black text-slate-900 mt-2 block">
            ₹{payments.length ? (totalRevenue / payments.length).toFixed(2) : '0.00'}
          </span>
          <p className="text-xs text-slate-500 mt-2">
            Standard tariff ₹40/hr (Car) • ₹20/hr (Bike)
          </p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
          <span className="text-xs text-slate-400 block font-medium uppercase tracking-wider">UPI Digital Share</span>
          <span className="text-3xl font-black text-emerald-600 mt-2 block">
            {payments.length
              ? `${Math.round(((revenueByMode['UPI'] || 0) / totalRevenue) * 100)}%`
              : '0%'}
          </span>
          <p className="text-xs text-slate-500 mt-2">
            ₹{(revenueByMode['UPI'] || 0).toFixed(2)} collected via UPI QR
          </p>
        </div>
      </div>

      {/* Breakdown Grids */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Revenue by Vehicle Type */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
          <h3 className="font-bold text-slate-900 text-sm mb-4 flex items-center gap-2">
            <Car className="w-4 h-4 text-blue-600" />
            <span>Revenue Breakdown by Vehicle Category</span>
          </h3>

          <div className="space-y-4">
            {[
              { label: 'Cars (4-Wheelers)', amount: revenueByVehicleType['Car'] || 0, color: 'bg-blue-600', icon: Car },
              { label: 'Bikes (2-Wheelers)', amount: revenueByVehicleType['Bike'] || 0, color: 'bg-indigo-600', icon: Bike },
              { label: 'EV Fast Charging Zone', amount: revenueByVehicleType['EV Car'] || 0, color: 'bg-cyan-500', icon: Zap }
            ].map(item => {
              const pct = totalRevenue > 0 ? (item.amount / totalRevenue) * 100 : 0;
              const Icon = item.icon;
              return (
                <div key={item.label} className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-slate-700 flex items-center gap-1.5">
                      <Icon className="w-3.5 h-3.5 text-slate-500" />
                      {item.label}
                    </span>
                    <span className="font-bold text-slate-900">
                      ₹{item.amount.toFixed(2)} ({pct.toFixed(1)}%)
                    </span>
                  </div>
                  <div className="w-full bg-slate-100 h-2 rounded-full overflow-hidden">
                    <div className={`${item.color} h-full rounded-full transition-all duration-300`} style={{ width: `${pct}%` }} />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Revenue by Payment Method */}
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
          <h3 className="font-bold text-slate-900 text-sm mb-4 flex items-center gap-2">
            <CreditCard className="w-4 h-4 text-emerald-600" />
            <span>Payment Modes &amp; Channels</span>
          </h3>

          <div className="space-y-4">
            {[
              { mode: 'UPI (QR & Apps)', amount: revenueByMode['UPI'] || 0, color: 'bg-emerald-500' },
              { mode: 'Credit / Debit Cards', amount: revenueByMode['Card'] || 0, color: 'bg-blue-500' },
              { mode: 'Cash at Gate Counter', amount: revenueByMode['Cash'] || 0, color: 'bg-amber-500' }
            ].map(item => {
              const pct = totalRevenue > 0 ? (item.amount / totalRevenue) * 100 : 0;
              return (
                <div key={item.mode} className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-slate-700">{item.mode}</span>
                    <span className="font-bold text-slate-900">
                      ₹{item.amount.toFixed(2)} ({pct.toFixed(1)}%)
                    </span>
                  </div>
                  <div className="w-full bg-slate-100 h-2 rounded-full overflow-hidden">
                    <div className={`${item.color} h-full rounded-full transition-all duration-300`} style={{ width: `${pct}%` }} />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};
