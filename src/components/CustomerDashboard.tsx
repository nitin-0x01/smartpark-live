import React, { useState } from 'react';
import { DatabaseState, DatabaseManager } from '../mockDb';
import { ParkingSlot, Reservation, Ticket, VehicleCategory } from '../types';
import { LiveSlotMap } from './LiveSlotMap';
import { QRTicketModal } from './QRTicketModal';
import { createBackendBooking } from '../backendApi';
import {
  Car,
  Bike,
  Zap,
  CheckCircle2,
  AlertCircle,
  MapPin,
  Calendar,
  Clock,
  QrCode,
  Compass,
  ArrowRight,
  ShieldCheck,
  Navigation,
  Sparkles
} from 'lucide-react';

interface CustomerDashboardProps {
  dbState: DatabaseState;
  onRefreshDb: () => void;
}

export const CustomerDashboard: React.FC<CustomerDashboardProps> = ({ dbState, onRefreshDb }) => {
  // Booking Form State
  const [vehicleType, setVehicleType] = useState<VehicleCategory>('Car');
  const [regNo, setRegNo] = useState<string>('');
  const [ownerName, setOwnerName] = useState<string>('');
  const [phone, setPhone] = useState<string>('');
  const [selectedSlotId, setSelectedSlotId] = useState<number | null>(null);

  // Status & Modal states
  const [statusMessage, setStatusMessage] = useState<{ text: string; isError: boolean } | null>(null);
  const [activePass, setActivePass] = useState<Reservation | Ticket | null>(null);
  const [isPassReservation, setIsPassReservation] = useState<boolean>(true);

  // 15 slots filtered by selected vehicle type
  const matchingSlots = dbState.slots.filter(s => s.type_name === vehicleType);
  const freeSlots = matchingSlots.filter(s => s.status === 'AVAILABLE');

  // Currently selected slot details for wayfinding
  const selectedSlot = dbState.slots.find(s => s.slot_id === selectedSlotId);

  // Customer's existing bookings / tickets
  const activeReservations = dbState.reservations.filter(r => r.status === 'CONFIRMED');
  const activeTickets = dbState.tickets.filter(t => t.status === 'ACTIVE');

  const handleBookingSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setStatusMessage(null);

    const cleanReg = regNo.trim().toUpperCase();
    if (!cleanReg) {
      setStatusMessage({ text: 'Please enter a valid vehicle registration number!', isError: true });
      return;
    }

    if (!selectedSlotId) {
      setStatusMessage({ text: 'Please choose an available parking bay from the list below!', isError: true });
      return;
    }

    try {
      const res = await createBackendBooking(
        ownerName.trim() || 'Driver',
        phone.trim() || '9876543210',
        cleanReg,
        vehicleType,
        selectedSlotId
      );
      setStatusMessage({ text: res.message, isError: false });
      setActivePass(res.reservation);
      setIsPassReservation(true);
      setRegNo('');
      setSelectedSlotId(null);
      await onRefreshDb();
    } catch (error) {
      setStatusMessage({
        text: error instanceof Error ? error.message : 'Booking failed. Please try again.',
        isError: true
      });
    }
  };

  const handleCancelBooking = (resId: number) => {
    if (window.confirm('Are you sure you want to cancel this booking? The bay will be released.')) {
      DatabaseManager.cancelReservation(resId);
      onRefreshDb();
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Welcome & Quick Booking Hero */}
      <div className="bg-gradient-to-r from-blue-900 via-indigo-900 to-slate-900 text-white rounded-3xl p-6 sm:p-8 shadow-xl relative overflow-hidden">
        <div className="relative z-10 max-w-2xl">
          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-blue-500/30 text-blue-200 border border-blue-400/30 mb-3">
            <Sparkles className="w-3.5 h-3.5 text-blue-300" />
            Instant Digital Parking Reservation &amp; QR Pass
          </div>
          <h1 className="text-2xl sm:text-3xl font-black tracking-tight leading-tight">
            Reserve Your Parking Bay in Advance
          </h1>
          <p className="text-blue-200 text-xs sm:text-sm mt-2 leading-relaxed">
            Choose your vehicle type, pick an open bay, and get an instant QR pass for automated boom barrier entry.
          </p>
        </div>

        {/* Live Counters */}
        <div className="grid grid-cols-3 gap-3 mt-6 pt-5 border-t border-white/10 relative z-10">
          <div className="bg-white/10 backdrop-blur-xs rounded-xl p-3 text-center">
            <span className="text-[11px] text-blue-200 block font-medium">Free Car Bays</span>
            <span className="text-xl sm:text-2xl font-black text-emerald-400">
              {dbState.slots.filter(s => s.type_name === 'Car' && s.status === 'AVAILABLE').length} Open
            </span>
          </div>
          <div className="bg-white/10 backdrop-blur-xs rounded-xl p-3 text-center">
            <span className="text-[11px] text-blue-200 block font-medium">Free Bike Bays</span>
            <span className="text-xl sm:text-2xl font-black text-emerald-400">
              {dbState.slots.filter(s => s.type_name === 'Bike' && s.status === 'AVAILABLE').length} Open
            </span>
          </div>
          <div className="bg-white/10 backdrop-blur-xs rounded-xl p-3 text-center">
            <span className="text-[11px] text-blue-200 block font-medium">EV Fast Chargers</span>
            <span className="text-xl sm:text-2xl font-black text-cyan-400">
              {dbState.slots.filter(s => s.is_ev && s.status === 'AVAILABLE').length} Open
            </span>
          </div>
        </div>
      </div>

      {/* Booking Form Card (3 Simple Steps) */}
      <div className="bg-white rounded-2xl border border-slate-200 p-6 shadow-xs">
        <div className="border-b border-slate-200 pb-4 mb-5">
          <h2 className="text-lg font-black text-slate-900 tracking-tight flex items-center gap-2">
            <Calendar className="w-5 h-5 text-blue-600" />
            <span>Book a Parking Slot (3 Easy Steps)</span>
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Select vehicle type, enter registration number, and pick an available bay
          </p>
        </div>

        {statusMessage && (
          <div
            className={`p-3.5 rounded-xl mb-5 text-xs font-semibold flex items-center gap-2 ${
              statusMessage.isError
                ? 'bg-rose-50 text-rose-700 border border-rose-200'
                : 'bg-emerald-50 text-emerald-800 border border-emerald-200'
            }`}
          >
            {statusMessage.isError ? <AlertCircle className="w-4 h-4 shrink-0" /> : <CheckCircle2 className="w-4 h-4 shrink-0" />}
            <span>{statusMessage.text}</span>
          </div>
        )}

        <form onSubmit={handleBookingSubmit} className="space-y-5">
          {/* STEP 1: Select Vehicle Type */}
          <div>
            <label className="text-xs font-bold text-slate-800 uppercase tracking-wider block mb-2">
              Step 1: Select Vehicle Type
            </label>
            <div className="grid grid-cols-3 gap-3">
              {[
                { type: 'Car' as const, label: 'Car / SUV', rate: '₹40 / hr', icon: Car, color: 'border-blue-600 text-blue-900 bg-blue-50' },
                { type: 'Bike' as const, label: 'Two-Wheeler', rate: '₹20 / hr', icon: Bike, color: 'border-indigo-600 text-indigo-900 bg-indigo-50' },
                { type: 'EV Car' as const, label: 'EV Fast Charging', rate: '₹50 / hr', icon: Zap, color: 'border-cyan-600 text-cyan-900 bg-cyan-50' }
              ].map(item => {
                const Icon = item.icon;
                const isSelected = vehicleType === item.type;
                return (
                  <button
                    key={item.type}
                    type="button"
                    onClick={() => {
                      setVehicleType(item.type);
                      setSelectedSlotId(null);
                    }}
                    className={`p-3.5 rounded-xl border-2 transition flex flex-col items-center justify-center gap-1.5 ${
                      isSelected
                        ? `${item.color} ring-4 ring-blue-500/20 shadow-xs font-black`
                        : 'border-slate-200 hover:border-slate-300 text-slate-600 hover:bg-slate-50 font-bold'
                    }`}
                  >
                    <Icon className="w-5 h-5" />
                    <span className="text-xs">{item.label}</span>
                    <span className="text-[11px] font-semibold opacity-80">{item.rate}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* STEP 2: Vehicle Details */}
          <div>
            <label className="text-xs font-bold text-slate-800 uppercase tracking-wider block mb-2">
              Step 2: Enter Vehicle Number &amp; Contact
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <input
                  type="text"
                  placeholder="Vehicle Number (e.g. MH12AB1234)"
                  value={regNo}
                  onChange={e => setRegNo(e.target.value.toUpperCase())}
                  className="w-full text-xs font-mono font-bold uppercase p-3 bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  required
                />
              </div>
              <div>
                <input
                  type="text"
                  placeholder="Driver / Owner Name"
                  value={ownerName}
                  onChange={e => setOwnerName(e.target.value)}
                  className="w-full text-xs p-3 bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  required
                />
              </div>
              <div>
                <input
                  type="tel"
                  placeholder="Mobile Phone (10 digits)"
                  value={phone}
                  onChange={e => setPhone(e.target.value)}
                  className="w-full text-xs p-3 bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  required
                />
              </div>
            </div>
          </div>

          {/* STEP 3: Choose Available Slot from Live Database */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                Step 3: Choose Your Parking Bay ({freeSlots.length} available out of {matchingSlots.length})
              </label>
              <span className="text-[11px] text-slate-400">Click any green bay to select</span>
            </div>

            {freeSlots.length === 0 ? (
              <div className="p-4 bg-amber-50 border border-amber-200 text-amber-800 rounded-xl text-xs font-semibold text-center">
                All {vehicleType} bays are currently occupied. Please choose another vehicle type or check back in a few minutes.
              </div>
            ) : (
              <div className="space-y-3">
                {/* Dropdown Selector */}
                <div>
                  <select
                    value={selectedSlotId || ''}
                    onChange={e => setSelectedSlotId(e.target.value ? Number(e.target.value) : null)}
                    className="w-full text-xs font-semibold p-3 bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:ring-2 focus:ring-blue-500 focus:outline-none"
                  >
                    <option value="">-- Choose an Available Bay from Dropdown --</option>
                    {freeSlots.map(slot => (
                      <option key={slot.slot_id} value={slot.slot_id}>
                        Slot {slot.slot_number} ({slot.type_name}) &rarr; Directions: {slot.location_hint}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Visual Grid Buttons */}
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-2.5">
                  {matchingSlots.map(slot => {
                    const isFree = slot.status === 'AVAILABLE';
                    const isSelected = selectedSlotId === slot.slot_id;

                    return (
                      <button
                        key={slot.slot_id}
                        type="button"
                        disabled={!isFree}
                        onClick={() => setSelectedSlotId(slot.slot_id)}
                        className={`p-3 rounded-xl border-2 text-center transition flex flex-col items-center justify-center ${
                          isSelected
                            ? 'bg-blue-600 text-white border-blue-700 shadow-md ring-2 ring-blue-400'
                            : isFree
                            ? 'bg-emerald-50 text-emerald-900 border-emerald-300 hover:bg-emerald-100'
                            : 'bg-slate-100 text-slate-400 border-slate-200 opacity-60 cursor-not-allowed'
                        }`}
                      >
                        <span className="font-mono font-black text-sm">{slot.slot_number}</span>
                        <span className="text-[10px] font-bold mt-0.5">
                          {isFree ? 'OPEN' : slot.status}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
          </div>

          {/* WAYFINDING LOGIC: How does a new customer find this slot? */}
          {selectedSlot && (
            <div className="bg-blue-50 border-2 border-blue-200 rounded-2xl p-4 flex flex-wrap items-center justify-between gap-3 animate-in fade-in duration-150">
              <div className="flex items-start gap-3">
                <div className="p-2 bg-blue-600 text-white rounded-xl shrink-0 mt-0.5">
                  <Navigation className="w-5 h-5" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-xs text-blue-950 uppercase tracking-wider">
                      Wayfinding Navigation Guide for Bay {selectedSlot.slot_number}:
                    </span>
                    <span className="font-bold text-xs bg-blue-200 text-blue-900 px-2 py-0.5 rounded-md">
                      {selectedSlot.zone}
                    </span>
                  </div>
                  <p className="text-xs text-blue-800 font-medium mt-1">
                    {selectedSlot.location_hint}
                  </p>
                </div>
              </div>
              <div className="text-right">
                <span className="text-xs text-blue-700 block font-semibold">Estimated 2-Hr Total</span>
                <span className="text-lg font-black text-blue-950">
                  ₹{vehicleType === 'Car' ? '80.00' : vehicleType === 'Bike' ? '40.00' : '100.00'}
                </span>
              </div>
            </div>
          )}

          {/* Submit Action */}
          <div className="pt-2">
            <button
              type="submit"
              disabled={freeSlots.length === 0 || !selectedSlotId}
              className="w-full bg-blue-600 hover:bg-blue-700 text-white font-black py-3.5 px-6 rounded-xl text-sm transition shadow-lg flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span>Confirm Reservation &amp; Get QR Ticket</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        </form>
      </div>

      {/* Active Passes Section */}
      {(activeReservations.length > 0 || activeTickets.length > 0) && (
        <div className="space-y-3">
          <h2 className="text-base font-black text-slate-900 tracking-tight flex items-center gap-2">
            <QrCode className="w-5 h-5 text-blue-600" />
            <span>My Active Parking Passes (Scan at Gate Boom Barrier)</span>
          </h2>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {activeReservations.map(res => (
              <div
                key={res.reservation_id}
                className="bg-white rounded-2xl border-2 border-blue-200 p-5 shadow-xs hover:shadow-md transition"
              >
                <div className="flex items-center justify-between mb-3">
                  <span className="font-mono font-bold text-xs bg-blue-50 text-blue-700 px-2.5 py-1 rounded-md">
                    Pass #{res.reservation_id}
                  </span>
                  <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-amber-100 text-amber-800">
                    RESERVED
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-2 text-xs bg-slate-50 p-3 rounded-xl border border-slate-100 mb-3">
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase font-medium">Vehicle</span>
                    <span className="font-mono font-bold text-slate-800">{res.reg_no}</span>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase font-medium">Assigned Bay</span>
                    <span className="font-bold text-blue-700">Slot {res.slot_number}</span>
                  </div>
                  <div className="col-span-2">
                    <span className="text-[10px] text-slate-400 block uppercase font-medium">Location</span>
                    <span className="text-slate-600 text-[11px]">{res.location_hint}</span>
                  </div>
                </div>

                <div className="flex gap-2">
                  <button
                    onClick={() => {
                      setActivePass(res);
                      setIsPassReservation(true);
                    }}
                    className="flex-1 bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold py-2.5 rounded-xl transition flex items-center justify-center gap-1.5"
                  >
                    <QrCode className="w-4 h-4" />
                    <span>View QR Pass</span>
                  </button>
                  <button
                    onClick={() => handleCancelBooking(res.reservation_id)}
                    className="px-3 text-xs text-rose-600 hover:bg-rose-50 border border-rose-200 rounded-xl font-bold transition"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ))}

            {activeTickets.map(tkt => (
              <div
                key={tkt.ticket_id}
                className="bg-white rounded-2xl border-2 border-emerald-300 p-5 shadow-xs"
              >
                <div className="flex items-center justify-between mb-3">
                  <span className="font-mono font-bold text-xs bg-emerald-50 text-emerald-800 px-2.5 py-1 rounded-md">
                    Ticket #{tkt.ticket_id}
                  </span>
                  <span className="text-xs font-bold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800">
                    CURRENTLY PARKED
                  </span>
                </div>

                <div className="grid grid-cols-2 gap-2 text-xs bg-slate-50 p-3 rounded-xl border border-slate-100 mb-3">
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase font-medium">Vehicle</span>
                    <span className="font-mono font-bold text-slate-800">{tkt.reg_no}</span>
                  </div>
                  <div>
                    <span className="text-[10px] text-slate-400 block uppercase font-medium">Parked In</span>
                    <span className="font-bold text-emerald-700">Slot {tkt.slot_number}</span>
                  </div>
                  <div className="col-span-2">
                    <span className="text-[10px] text-slate-400 block uppercase font-medium">Location</span>
                    <span className="text-slate-600 text-[11px]">{tkt.location_hint}</span>
                  </div>
                </div>

                <button
                  onClick={() => {
                    setActivePass(tkt);
                    setIsPassReservation(false);
                  }}
                  className="w-full bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold py-2.5 rounded-xl transition flex items-center justify-center gap-1.5"
                >
                  <QrCode className="w-4 h-4" />
                  <span>Show Exit QR Pass</span>
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Live Floor Map (Strict Customer Privacy Enforced!) */}
      <div className="space-y-3">
        <LiveSlotMap
          slots={dbState.slots}
          selectedSlotId={selectedSlotId || undefined}
          onSelectSlot={slot => setSelectedSlotId(slot.slot_id)}
          isAdmin={false} // NEVER SHOW OCCUPANT NAMES TO CUSTOMERS!
        />
      </div>

      {/* QR Ticket Modal */}
      {activePass && (
        <QRTicketModal
          item={activePass}
          isReservation={isPassReservation}
          onClose={() => setActivePass(null)}
        />
      )}
    </div>
  );
};
