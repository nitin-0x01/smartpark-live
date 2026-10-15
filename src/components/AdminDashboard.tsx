import React, { useState } from 'react';
import { DatabaseState, DatabaseManager } from '../mockDb';
import { LiveSlotMap } from './LiveSlotMap';
import { QRTicketModal } from './QRTicketModal';
import { exitBackendVehicle, parkBackendVehicle, scanBackendTicket } from '../backendApi';
import { Ticket, Reservation } from '../types';
import {
  Car,
  Bike,
  Zap,
  TrendingUp,
  DollarSign,
  AlertCircle,
  CheckCircle,
  Clock,
  QrCode,
  Users,
  Search,
  ArrowRight,
  ShieldCheck,
  RotateCcw,
  SlidersHorizontal,
  KeyRound,
  Compass,
  CheckCircle2
} from 'lucide-react';

interface AdminDashboardProps {
  dbState: DatabaseState;
  onRefreshDb: () => void;
}

export const AdminDashboard: React.FC<AdminDashboardProps> = ({ dbState, onRefreshDb }) => {
  // Gate Mode Switch: QR Scanner vs Manual Gate Controls
  const [gateMode, setGateMode] = useState<'QR_SCAN' | 'MANUAL'>('QR_SCAN');

  // QR Scanner State
  const [qrInput, setQrInput] = useState<string>('');
  const [scanResult, setScanResult] = useState<{
    action: 'CHECK_IN' | 'CHECK_OUT' | 'INVALID';
    success: boolean;
    ticket?: Ticket;
    reservation?: Reservation;
    amount?: number;
    hours?: number;
    message: string;
  } | null>(null);

  // Manual Entry Form State (for walk-in drivers)
  const [manualType, setManualType] = useState<number>(1);
  const [manualReg, setManualReg] = useState<string>('');
  const [manualOwner, setManualOwner] = useState<string>('');
  const [manualPhone, setManualPhone] = useState<string>('');
  const [manualSlotId, setManualSlotId] = useState<number | null>(null);
  const [manualMessage, setManualMessage] = useState<{ text: string; isError: boolean } | null>(null);

  // Active Tickets Search & Filter
  const [searchFilter, setSearchFilter] = useState<string>('');
  const [viewTicket, setViewTicket] = useState<Ticket | null>(null);

  // Facility Metrics
  const totalSlots = dbState.slots.length; // 15
  const availableSlots = dbState.slots.filter(s => s.status === 'AVAILABLE').length;
  const occupiedSlots = dbState.slots.filter(s => s.status === 'OCCUPIED').length;
  const reservedSlots = dbState.slots.filter(s => s.status === 'RESERVED').length;
  const occupancyPercent = ((occupiedSlots + reservedSlots) / totalSlots) * 100;

  const todayRevenue = dbState.payments.reduce((sum, p) => sum + p.amount, 0);

  // Handle QR Scan
  const handleScanSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!qrInput.trim()) return;

    try {
      const res = await scanBackendTicket(qrInput);
      setScanResult(res);
      await onRefreshDb();
    } catch (error) {
      setScanResult({
        action: 'INVALID',
        success: false,
        message: error instanceof Error ? error.message : 'QR scan failed.'
      });
    }
  };

  // Complete checkout from QR scan
  const handleCompleteQrExit = async (ticketId: number, mode: 'UPI' | 'Cash' | 'Card') => {
    const regNo = scanResult?.ticket?.reg_no;
    if (!regNo) return;
    try {
      const res = await exitBackendVehicle(regNo, mode);
      alert(res.message);
      setScanResult(null);
      setQrInput('');
      await onRefreshDb();
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Checkout failed.');
    }
  };

  // Handle Manual Walk-in Entry
  const handleManualEntrySubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setManualMessage(null);

    if (!manualReg) {
      setManualMessage({ text: 'Vehicle registration number is required!', isError: true });
      return;
    }

    if (!manualSlotId) {
      setManualMessage({ text: 'Please pick an open slot from the list!', isError: true });
      return;
    }

    try {
      const res = await parkBackendVehicle(
        manualReg,
        manualOwner || 'Walk-in Driver',
        manualPhone || 'N/A',
        manualType
      );
      setManualMessage({ text: res.message, isError: false });
      setViewTicket(res.ticket);
      setManualReg('');
      setManualOwner('');
      setManualPhone('');
      setManualSlotId(null);
      await onRefreshDb();
    } catch (error) {
      setManualMessage({
        text: error instanceof Error ? error.message : 'Vehicle entry failed.',
        isError: true
      });
    }
  };

  // Handle Quick Exit from Active Table
  const handleQuickExit = (tkt: Ticket) => {
    const entryDate = new Date(tkt.entry_time);
    const now = new Date();
    const durationMin = Math.max(1, Math.round((now.getTime() - entryDate.getTime()) / (1000 * 60)));
    const hours = Math.max(1, Math.ceil(durationMin / 60));
    const rate = tkt.type_id === 1 ? 40 : tkt.type_id === 2 ? 20 : 50;
    const amount = hours * rate;

    const mode = window.confirm(`Exit ${tkt.reg_no} (Slot ${tkt.slot_number})?\nDuration: ${hours} hr(s)\nTotal Amount: ₹${amount.toFixed(2)}\n\nClick OK for UPI or Cancel for Cash.`)
      ? 'UPI'
      : 'Cash';

    void exitBackendVehicle(tkt.reg_no, mode)
      .then(res => {
        alert(res.message);
        return onRefreshDb();
      })
      .catch(error => alert(error instanceof Error ? error.message : 'Checkout failed.'));
  };

  // Filtered free slots for manual entry
  const availableSlotsForManual = dbState.slots.filter(
    s => s.type_id === manualType && s.status === 'AVAILABLE'
  );

  const activeTicketsList = dbState.tickets.filter(t => t.status === 'ACTIVE');
  const filteredActiveTickets = activeTicketsList.filter(
    t =>
      t.reg_no.toLowerCase().includes(searchFilter.toLowerCase()) ||
      t.owner_name.toLowerCase().includes(searchFilter.toLowerCase()) ||
      t.slot_number.toLowerCase().includes(searchFilter.toLowerCase())
  );

  return (
    <div className="space-y-6">
      {/* KPI Cards Strip */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Available Bays</span>
            <span className="p-2 rounded-xl bg-emerald-100 text-emerald-700">
              <CheckCircle className="w-5 h-5" />
            </span>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-black text-slate-900">{availableSlots}</span>
            <span className="text-xs text-slate-400 font-semibold">of {totalSlots} Total</span>
          </div>
          <div className="mt-3 w-full bg-slate-100 h-2 rounded-full overflow-hidden">
            <div
              className="bg-emerald-500 h-full rounded-full transition-all duration-300"
              style={{ width: `${(availableSlots / totalSlots) * 100}%` }}
            />
          </div>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Parked &amp; Reserved</span>
            <span className="p-2 rounded-xl bg-rose-100 text-rose-700">
              <Car className="w-5 h-5" />
            </span>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-black text-rose-600">{occupiedSlots}</span>
            <span className="text-xs text-amber-600 font-bold">+{reservedSlots} reserved</span>
          </div>
          <p className="text-xs text-slate-400 mt-2">
            Occupancy: <strong className="text-slate-800">{occupancyPercent.toFixed(1)}%</strong>
          </p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">Total Revenue</span>
            <span className="p-2 rounded-xl bg-blue-100 text-blue-700">
              <TrendingUp className="w-5 h-5" />
            </span>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-black text-slate-900">₹{todayRevenue.toFixed(2)}</span>
          </div>
          <p className="text-xs text-slate-400 mt-2 font-medium">
            {dbState.payments.length} transactions processed
          </p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-cyan-800 uppercase tracking-wider">EV Fast Chargers</span>
            <span className="p-2 rounded-xl bg-cyan-100 text-cyan-700">
              <Zap className="w-5 h-5" />
            </span>
          </div>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="text-3xl font-black text-cyan-950">
              {dbState.slots.filter(s => s.is_ev && s.status === 'AVAILABLE').length}
            </span>
            <span className="text-xs text-slate-400 font-semibold">of 3 Guns Open</span>
          </div>
          <p className="text-xs text-cyan-700 mt-2 font-bold">
            22kW AC &amp; 50kW DC
          </p>
        </div>
      </div>

      {/* DUAL GATE CONTROLLER: QR BARRIER SCANNER VS MANUAL WALK-IN ENTRY */}
      <div className="bg-white rounded-3xl border border-slate-200 p-6 shadow-xs">
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 pb-4 mb-5">
          <div>
            <h2 className="text-lg font-black text-slate-900 tracking-tight flex items-center gap-2">
              <ShieldCheck className="w-5 h-5 text-blue-600" />
              <span>Gate Boom Barrier Operations Controller</span>
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Handle advance QR code arrivals or issue manual tickets for walk-in drivers
            </p>
          </div>

          <div className="flex items-center bg-slate-100 p-1 rounded-xl">
            <button
              onClick={() => setGateMode('QR_SCAN')}
              className={`px-4 py-2 rounded-lg text-xs font-bold transition flex items-center gap-1.5 ${
                gateMode === 'QR_SCAN'
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <QrCode className="w-4 h-4" />
              <span>QR Barrier Scanner (Pre-Booked)</span>
            </button>
            <button
              onClick={() => setGateMode('MANUAL')}
              className={`px-4 py-2 rounded-lg text-xs font-bold transition flex items-center gap-1.5 ${
                gateMode === 'MANUAL'
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <SlidersHorizontal className="w-4 h-4" />
              <span>Manual Entry &amp; Exit (Walk-in)</span>
            </button>
          </div>
        </div>

        {/* MODE 1: QR CODE BARRIER SCANNER */}
        {gateMode === 'QR_SCAN' && (
          <div className="space-y-4">
            <div className="max-w-xl mx-auto text-center">
              <span className="text-xs font-bold text-slate-700 block mb-2 uppercase tracking-wider">
                Optical QR Scanner Input (Entry Gate &amp; Exit Gate)
              </span>

              <form onSubmit={handleScanSubmit} className="flex gap-2">
                <input
                  type="text"
                  placeholder="Scan QR Code or Type Ticket/Pass ID or Reg No..."
                  value={qrInput}
                  onChange={e => setQrInput(e.target.value)}
                  className="flex-1 font-mono font-bold text-xs p-3 bg-slate-50 border-2 border-slate-200 rounded-xl focus:bg-white focus:border-blue-500 focus:outline-none uppercase"
                  autoFocus
                />
                <button
                  type="submit"
                  className="bg-blue-600 hover:bg-blue-700 text-white font-bold text-xs px-5 rounded-xl transition shadow-md flex items-center gap-1.5"
                >
                  <Search className="w-4 h-4" />
                  <span>Scan</span>
                </button>
              </form>

              <div className="flex items-center justify-center gap-2 mt-2 text-[11px] text-slate-400">
                <span>Quick Test:</span>
                <button
                  type="button"
                  onClick={() => {
                    const testRes = dbState.reservations.find(r => r.status === 'CONFIRMED');
                    if (testRes) setQrInput(testRes.qr_code);
                  }}
                  className="text-blue-600 hover:underline font-mono"
                >
                  Test Entry Reservation
                </button>
                <span>•</span>
                <button
                  type="button"
                  onClick={() => {
                    const testTkt = dbState.tickets.find(t => t.status === 'ACTIVE');
                    if (testTkt) setQrInput(testTkt.qr_code);
                  }}
                  className="text-emerald-600 hover:underline font-mono"
                >
                  Test Exit Checkout
                </button>
              </div>
            </div>

            {/* SCAN RESULT CARD */}
            {scanResult && (
              <div
                className={`max-w-xl mx-auto rounded-2xl border-2 p-5 ${
                  scanResult.action === 'CHECK_IN'
                    ? 'bg-emerald-50 border-emerald-300'
                    : scanResult.action === 'CHECK_OUT'
                    ? 'bg-blue-50 border-blue-300'
                    : 'bg-rose-50 border-rose-300'
                }`}
              >
                <div className="flex items-center justify-between pb-3 border-b border-slate-200/60">
                  <div className="flex items-center gap-2">
                    <CheckCircle2
                      className={`w-5 h-5 ${
                        scanResult.success ? 'text-emerald-600' : 'text-rose-600'
                      }`}
                    />
                    <span className="font-bold text-xs uppercase tracking-wider text-slate-900">
                      {scanResult.action === 'CHECK_IN'
                        ? 'GATE 1 ENTRY CHECK-IN VERIFIED'
                        : scanResult.action === 'CHECK_OUT'
                        ? 'GATE 2 EXIT CHECK-OUT VERIFIED'
                        : 'SCAN ERROR'}
                    </span>
                  </div>
                  <span className="text-xs font-mono font-bold bg-white px-2.5 py-0.5 rounded-md shadow-xs">
                    {scanResult.reservation?.reg_no || scanResult.ticket?.reg_no || 'INVALID'}
                  </span>
                </div>

                <p className="text-xs text-slate-700 my-3 font-semibold">
                  {scanResult.message}
                </p>

                {/* If Check In */}
                {scanResult.action === 'CHECK_IN' && scanResult.ticket && (
                  <div className="bg-white p-3.5 rounded-xl border border-emerald-200 space-y-1.5 text-xs">
                    <p className="font-bold text-emerald-950">
                      Assigned Bay: Slot {scanResult.ticket.slot_number}
                    </p>
                    <p className="text-slate-600 text-[11px]">
                      Directions: {scanResult.ticket.location_hint}
                    </p>
                    <div className="pt-2 flex justify-between items-center text-emerald-700 font-bold">
                      <span>Boom Barrier Opened for Entry!</span>
                      <button
                        onClick={() => setScanResult(null)}
                        className="bg-emerald-600 text-white px-3 py-1.5 rounded-lg text-xs"
                      >
                        Done
                      </button>
                    </div>
                  </div>
                )}

                {/* If Check Out */}
                {scanResult.action === 'CHECK_OUT' && scanResult.ticket && (
                  <div className="bg-white p-3.5 rounded-xl border border-blue-200 space-y-3 text-xs">
                    <div className="flex items-center justify-between">
                      <div>
                        <span className="text-[10px] uppercase text-slate-400 font-bold block">
                          Total Billable Duration
                        </span>
                        <span className="font-bold text-slate-800 text-sm">
                          {scanResult.hours} {scanResult.hours === 1 ? 'Hour' : 'Hours'}
                        </span>
                      </div>
                      <div className="text-right">
                        <span className="text-[10px] uppercase text-slate-400 font-bold block">
                          Total Payable Fare
                        </span>
                        <span className="text-xl font-black text-emerald-600">
                          ₹{scanResult.amount?.toFixed(2)}
                        </span>
                      </div>
                    </div>

                    <div className="pt-2 border-t border-slate-100 flex gap-2">
                      <button
                        onClick={() => handleCompleteQrExit(scanResult.ticket!.ticket_id, 'UPI')}
                        className="flex-1 bg-emerald-600 hover:bg-emerald-700 text-white font-bold py-2 rounded-xl text-xs transition"
                      >
                        Pay via UPI &amp; Open Gate
                      </button>
                      <button
                        onClick={() => handleCompleteQrExit(scanResult.ticket!.ticket_id, 'Cash')}
                        className="flex-1 bg-slate-900 hover:bg-slate-800 text-white font-bold py-2 rounded-xl text-xs transition"
                      >
                        Cash Received &amp; Open Gate
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* MODE 2: MANUAL ENTRY & EXIT CONTROLS (FOR WALK-IN DRIVERS) */}
        {gateMode === 'MANUAL' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* Walk-in Entry Form */}
            <div className="border border-slate-200 rounded-2xl p-4 bg-slate-50/50">
              <h3 className="font-bold text-xs uppercase tracking-wider text-slate-800 mb-3 flex items-center gap-1.5">
                <Car className="w-4 h-4 text-blue-600" />
                <span>Manual Walk-in Check-In (No Booking)</span>
              </h3>

              {manualMessage && (
                <div
                  className={`p-2.5 rounded-xl mb-3 text-xs font-semibold ${
                    manualMessage.isError
                      ? 'bg-rose-50 text-rose-700 border border-rose-200'
                      : 'bg-emerald-50 text-emerald-800 border border-emerald-200'
                  }`}
                >
                  {manualMessage.text}
                </div>
              )}

              <form onSubmit={handleManualEntrySubmit} className="space-y-3">
                <div className="grid grid-cols-3 gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      setManualType(1);
                      setManualSlotId(null);
                    }}
                    className={`py-1.5 px-2 rounded-lg text-xs font-bold border transition ${
                      manualType === 1 ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-slate-600'
                    }`}
                  >
                    Car (₹40)
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setManualType(2);
                      setManualSlotId(null);
                    }}
                    className={`py-1.5 px-2 rounded-lg text-xs font-bold border transition ${
                      manualType === 2 ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-slate-600'
                    }`}
                  >
                    Bike (₹20)
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setManualType(3);
                      setManualSlotId(null);
                    }}
                    className={`py-1.5 px-2 rounded-lg text-xs font-bold border transition ${
                      manualType === 3 ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-slate-600'
                    }`}
                  >
                    EV (₹50)
                  </button>
                </div>

                <input
                  type="text"
                  placeholder="Registration No (e.g. MH12AB1234)"
                  value={manualReg}
                  onChange={e => setManualReg(e.target.value.toUpperCase())}
                  className="w-full text-xs font-mono font-bold uppercase p-2.5 bg-white border border-slate-200 rounded-xl"
                  required
                />

                <div className="grid grid-cols-2 gap-2">
                  <input
                    type="text"
                    placeholder="Owner Name (optional)"
                    value={manualOwner}
                    onChange={e => setManualOwner(e.target.value)}
                    className="w-full text-xs p-2.5 bg-white border border-slate-200 rounded-xl"
                  />
                  <input
                    type="tel"
                    placeholder="Phone (optional)"
                    value={manualPhone}
                    onChange={e => setManualPhone(e.target.value)}
                    className="w-full text-xs p-2.5 bg-white border border-slate-200 rounded-xl"
                  />
                </div>

                <div>
                  <label className="text-[11px] font-bold text-slate-500 uppercase block mb-1">
                    Assign Available Bay:
                  </label>
                  <div className="grid grid-cols-4 gap-2">
                    {availableSlotsForManual.map(s => (
                      <button
                        key={s.slot_id}
                        type="button"
                        onClick={() => setManualSlotId(s.slot_id)}
                        className={`p-2 rounded-lg border text-xs font-mono font-bold transition ${
                          manualSlotId === s.slot_id
                            ? 'bg-blue-600 text-white border-blue-700'
                            : 'bg-white text-slate-700 border-slate-200 hover:bg-slate-100'
                        }`}
                      >
                        {s.slot_number}
                      </button>
                    ))}
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={availableSlotsForManual.length === 0 || !manualSlotId}
                  className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-2.5 rounded-xl text-xs transition shadow-sm disabled:opacity-50"
                >
                  Park Vehicle &amp; Open Gate
                </button>
              </form>
            </div>

            {/* Quick Exit Guidance */}
            <div className="border border-slate-200 rounded-2xl p-4 bg-slate-50/50 flex flex-col justify-between">
              <div>
                <h3 className="font-bold text-xs uppercase tracking-wider text-slate-800 mb-2 flex items-center gap-1.5">
                  <DollarSign className="w-4 h-4 text-emerald-600" />
                  <span>Quick Vehicle Exit &amp; Checkout</span>
                </h3>
                <p className="text-xs text-slate-500 mb-3">
                  Click "Exit &amp; Bill" next to any currently parked vehicle in the table below to calculate fare and release the bay.
                </p>
                <div className="p-3 bg-white border border-slate-200 rounded-xl text-xs space-y-1.5">
                  <div className="flex justify-between font-semibold">
                    <span>Cars:</span> <span>₹40.00 / hr</span>
                  </div>
                  <div className="flex justify-between font-semibold">
                    <span>Bikes:</span> <span>₹20.00 / hr</span>
                  </div>
                  <div className="flex justify-between font-semibold">
                    <span>EV Fast Charging:</span> <span>₹50.00 / hr</span>
                  </div>
                  <span className="block text-[11px] text-slate-400 pt-1 border-t border-slate-100">
                    Rule: Minimum 1 hour, rounded up on partial hours.
                  </span>
                </div>
              </div>

              <div className="text-xs text-slate-500 pt-3">
                Total Parked: <strong className="text-slate-800">{activeTicketsList.length} vehicles</strong>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* LIVE 15-SLOT PARKING MAP (ADMIN AUDIT MODE: DETAILS VISIBLE!) */}
      <div className="space-y-3">
        <LiveSlotMap
          slots={dbState.slots}
          isAdmin={true} // ADMIN SEES OCCUPANT DETAILS!
        />
      </div>

      {/* CURRENTLY PARKED VEHICLES AUDIT TABLE */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="p-5 border-b border-slate-200 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h3 className="font-bold text-slate-900 text-base flex items-center gap-2">
              <span>Currently Parked Vehicles</span>
              <span className="px-2.5 py-0.5 rounded-full text-xs font-black bg-blue-100 text-blue-800">
                {activeTicketsList.length} Active
              </span>
            </h3>
            <p className="text-xs text-slate-400">Real-time occupancy log from database</p>
          </div>

          <div className="relative min-w-[220px]">
            <Search className="w-3.5 h-3.5 absolute left-3 top-3 text-slate-400" />
            <input
              type="text"
              placeholder="Search Reg, Owner, Slot..."
              value={searchFilter}
              onChange={e => setSearchFilter(e.target.value)}
              className="w-full text-xs pl-8 pr-3 py-2 bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
        </div>

        {filteredActiveTickets.length === 0 ? (
          <div className="p-8 text-center text-xs text-slate-400">
            No parked vehicles found.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 border-b border-slate-200 text-slate-600 uppercase font-semibold text-[11px]">
                <tr>
                  <th className="p-3">Ticket ID</th>
                  <th className="p-3">Vehicle No</th>
                  <th className="p-3">Owner / Contact</th>
                  <th className="p-3">Bay</th>
                  <th className="p-3">Entry Time</th>
                  <th className="p-3">Duration</th>
                  <th className="p-3 text-right">Gate Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredActiveTickets.map(tkt => {
                  const entryDate = new Date(tkt.entry_time);
                  const durationMin = Math.max(1, Math.round((Date.now() - entryDate.getTime()) / (1000 * 60)));
                  const hours = Math.max(1, Math.ceil(durationMin / 60));
                  const rate = tkt.type_id === 1 ? 40 : tkt.type_id === 2 ? 20 : 50;
                  const estimatedFare = hours * rate;

                  return (
                    <tr key={tkt.ticket_id} className="hover:bg-slate-50/70">
                      <td className="p-3 font-mono font-bold text-blue-700">#{tkt.ticket_id}</td>
                      <td className="p-3 font-mono font-bold text-slate-900">{tkt.reg_no}</td>
                      <td className="p-3 text-slate-700">
                        {tkt.owner_name} <span className="text-slate-400 text-[10px]">({tkt.phone})</span>
                      </td>
                      <td className="p-3">
                        <span className="font-mono font-black text-xs text-blue-800 bg-blue-50 px-2 py-0.5 rounded-md">
                          {tkt.slot_number}
                        </span>
                      </td>
                      <td className="p-3 text-slate-500">
                        {entryDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </td>
                      <td className="p-3 font-medium text-slate-600">
                        {durationMin < 60 ? `${durationMin} mins` : `${(durationMin / 60).toFixed(1)} hrs`}
                      </td>
                      <td className="p-3 text-right space-x-2">
                        <button
                          onClick={() => setViewTicket(tkt)}
                          className="text-xs text-blue-600 hover:text-blue-800 font-bold inline-flex items-center gap-1"
                        >
                          <QrCode className="w-3.5 h-3.5" />
                          <span>Pass</span>
                        </button>
                        <button
                          onClick={() => handleQuickExit(tkt)}
                          className="text-xs bg-emerald-600 hover:bg-emerald-700 text-white font-bold px-3 py-1 rounded-lg shadow-xs"
                        >
                          Exit &amp; Bill (₹{estimatedFare})
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* QR Ticket View Modal */}
      {viewTicket && (
        <QRTicketModal
          item={viewTicket}
          isReservation={false}
          onClose={() => setViewTicket(null)}
        />
      )}
    </div>
  );
};
