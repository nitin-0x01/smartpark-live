import React, { useState } from 'react';
import { ParkingSlot, SlotStatus } from '../types';
import {
  Car,
  Bike,
  Zap,
  CheckCircle2,
  Lock,
  AlertTriangle,
  Compass,
  MapPin,
  Eye,
  EyeOff,
  Navigation,
  ShieldAlert,
  ArrowRight
} from 'lucide-react';

interface LiveSlotMapProps {
  slots: ParkingSlot[];
  selectedSlotId?: number;
  onSelectSlot?: (slot: ParkingSlot) => void;
  isAdmin?: boolean;
}

export const LiveSlotMap: React.FC<LiveSlotMapProps> = ({
  slots,
  selectedSlotId,
  onSelectSlot,
  isAdmin = false
}) => {
  const [filterType, setFilterType] = useState<'ALL' | 'CAR' | 'BIKE' | 'EV'>('ALL');
  const [activeSlot, setActiveSlot] = useState<ParkingSlot | null>(null);

  const filteredSlots = slots.filter(slot => {
    if (filterType === 'CAR') return slot.type_name === 'Car';
    if (filterType === 'BIKE') return slot.type_name === 'Bike';
    if (filterType === 'EV') return slot.is_ev;
    return true;
  });

  const getStatusColor = (slot: ParkingSlot) => {
    if (slot.is_ev && slot.status === 'OCCUPIED') {
      return 'bg-cyan-500 text-white border-cyan-600';
    }
    switch (slot.status) {
      case 'AVAILABLE':
        return 'bg-emerald-500 text-white border-emerald-600 shadow-emerald-100';
      case 'OCCUPIED':
        return 'bg-rose-500 text-white border-rose-600 shadow-rose-100';
      case 'RESERVED':
        return 'bg-amber-400 text-amber-950 border-amber-500 shadow-amber-100';
      default:
        return 'bg-slate-300 text-slate-700 border-slate-400';
    }
  };

  const availableCount = slots.filter(s => s.status === 'AVAILABLE').length;
  const occupiedCount = slots.filter(s => s.status === 'OCCUPIED').length;
  const reservedCount = slots.filter(s => s.status === 'RESERVED').length;

  return (
    <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
      {/* Top Header Bar */}
      <div className="p-4 sm:p-5 border-b border-slate-200 flex flex-wrap items-center justify-between gap-4 bg-slate-50/80">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-base sm:text-lg font-black text-slate-900 tracking-tight flex items-center gap-2">
              <Compass className="w-5 h-5 text-blue-600" />
              <span>Live Parking Floor Map</span>
            </h2>
            {/* Privacy indicator */}
            {!isAdmin ? (
              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-slate-500 bg-white px-2 py-0.5 rounded-md border border-slate-200">
                <EyeOff className="w-3 h-3 text-slate-400" />
                Privacy Protected Mode
              </span>
            ) : (
              <span className="inline-flex items-center gap-1 text-[11px] font-bold text-blue-700 bg-blue-50 px-2 py-0.5 rounded-md border border-blue-200">
                <Eye className="w-3 h-3 text-blue-600" />
                Admin Audit Mode (Vehicle details visible)
              </span>
            )}
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            Real-time status across 15 bays • Select any available bay to view location directions
          </p>
        </div>

        {/* Filter Buttons */}
        <div className="flex items-center gap-1 bg-white p-1 rounded-xl border border-slate-200 shadow-xs">
          <button
            onClick={() => setFilterType('ALL')}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition ${
              filterType === 'ALL' ? 'bg-blue-600 text-white shadow-xs' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            All (15)
          </button>
          <button
            onClick={() => setFilterType('CAR')}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition flex items-center gap-1 ${
              filterType === 'CAR' ? 'bg-blue-600 text-white shadow-xs' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            <Car className="w-3.5 h-3.5" /> Cars (6)
          </button>
          <button
            onClick={() => setFilterType('BIKE')}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition flex items-center gap-1 ${
              filterType === 'BIKE' ? 'bg-blue-600 text-white shadow-xs' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            <Bike className="w-3.5 h-3.5" /> Bikes (6)
          </button>
          <button
            onClick={() => setFilterType('EV')}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition flex items-center gap-1 ${
              filterType === 'EV' ? 'bg-cyan-600 text-white shadow-xs' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            <Zap className="w-3.5 h-3.5" /> EV Fast (3)
          </button>
        </div>
      </div>

      {/* Status Bar */}
      <div className="px-5 py-2.5 bg-slate-100/90 border-b border-slate-200 flex flex-wrap items-center justify-between text-xs font-bold gap-3">
        <div className="flex items-center gap-4 flex-wrap">
          <div className="flex items-center gap-1.5">
            <span className="w-3.5 h-3.5 rounded-md bg-emerald-500 inline-block shadow-xs" />
            <span className="text-slate-700">Available ({availableCount})</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-3.5 h-3.5 rounded-md bg-rose-500 inline-block shadow-xs" />
            <span className="text-slate-700">Occupied ({occupiedCount})</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-3.5 h-3.5 rounded-md bg-amber-400 inline-block shadow-xs" />
            <span className="text-slate-700">Reserved ({reservedCount})</span>
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-3.5 h-3.5 rounded-md bg-cyan-500 inline-block shadow-xs" />
            <span className="text-cyan-800">EV Fast Station (3)</span>
          </div>
        </div>

        <span className="text-[11px] text-slate-500 font-medium">
          Green = Available to park • Red = Bay occupied
        </span>
      </div>

      {/* Parking Map Floor Layout */}
      <div className="p-6 space-y-6 bg-slate-900/[0.01]">
        {/* SECTION 1: Ground Floor - Zone A (Cars C1 - C4) */}
        {(filterType === 'ALL' || filterType === 'CAR') && (
          <div className="border border-slate-200 rounded-2xl p-4 bg-white shadow-xs">
            <div className="flex items-center justify-between mb-3 border-b border-slate-100 pb-2">
              <div className="flex items-center gap-2">
                <span className="p-1 rounded-md bg-blue-100 text-blue-700">
                  <Car className="w-4 h-4" />
                </span>
                <div>
                  <h3 className="font-bold text-xs uppercase tracking-wider text-slate-800">
                    Ground Floor • Zone A (Cars C1 - C4)
                  </h3>
                  <span className="text-[11px] text-slate-400">Standard Car Rate: ₹40.00 / hour</span>
                </div>
              </div>
              <span className="text-[11px] font-semibold text-blue-600 bg-blue-50 px-2 py-0.5 rounded-md">
                Gate 1 Entry Lane
              </span>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {slots
                .filter(s => s.type_name === 'Car' && s.zone === 'Zone A')
                .map(slot => renderSlot(slot))}
            </div>
          </div>
        )}

        {/* SECTION 2: Level P1 - Zone C (Cars C5 - C6) */}
        {(filterType === 'ALL' || filterType === 'CAR') && (
          <div className="border border-slate-200 rounded-2xl p-4 bg-white shadow-xs">
            <div className="flex items-center justify-between mb-3 border-b border-slate-100 pb-2">
              <div className="flex items-center gap-2">
                <span className="p-1 rounded-md bg-indigo-100 text-indigo-700">
                  <Car className="w-4 h-4" />
                </span>
                <div>
                  <h3 className="font-bold text-xs uppercase tracking-wider text-slate-800">
                    Level P1 • Zone C (Upper Deck Cars C5 - C6)
                  </h3>
                  <span className="text-[11px] text-slate-400">Wide bays via Ramp • ₹40.00 / hour</span>
                </div>
              </div>
              <span className="text-[11px] font-semibold text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded-md">
                Ramp Accessible
              </span>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              {slots
                .filter(s => s.type_name === 'Car' && s.zone === 'Zone C')
                .map(slot => renderSlot(slot))}
            </div>
          </div>
        )}

        {/* SECTION 3: Ground Floor - Zone B (Two-Wheelers B1 - B6) */}
        {(filterType === 'ALL' || filterType === 'BIKE') && (
          <div className="border border-slate-200 rounded-2xl p-4 bg-white shadow-xs">
            <div className="flex items-center justify-between mb-3 border-b border-slate-100 pb-2">
              <div className="flex items-center gap-2">
                <span className="p-1 rounded-md bg-emerald-100 text-emerald-700">
                  <Bike className="w-4 h-4" />
                </span>
                <div>
                  <h3 className="font-bold text-xs uppercase tracking-wider text-slate-800">
                    Ground Floor • Zone B (Two-Wheeler Bays B1 - B6)
                  </h3>
                  <span className="text-[11px] text-slate-400">Motorcycles &amp; Scooters • ₹20.00 / hour</span>
                </div>
              </div>
              <span className="text-[11px] font-semibold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-md">
                Right of Main Gate
              </span>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-3">
              {slots
                .filter(s => s.type_name === 'Bike')
                .map(slot => renderSlot(slot))}
            </div>
          </div>
        )}

        {/* SECTION 4: EV Charging Zone (EV1 - EV3) */}
        {(filterType === 'ALL' || filterType === 'EV') && (
          <div className="border border-cyan-200 rounded-2xl p-4 bg-cyan-50/30 shadow-xs">
            <div className="flex items-center justify-between mb-3 border-b border-cyan-200/80 pb-2">
              <div className="flex items-center gap-2">
                <span className="p-1 rounded-md bg-cyan-500 text-white">
                  <Zap className="w-4 h-4" />
                </span>
                <div>
                  <h3 className="font-bold text-xs uppercase tracking-wider text-cyan-950">
                    Dedicated EV Green Hub (Fast Chargers EV1 - EV3)
                  </h3>
                  <span className="text-[11px] text-cyan-800">
                    22kW AC &amp; 50kW DC Fast Chargers • ₹50.00 / hour
                  </span>
                </div>
              </div>
              <span className="text-[11px] font-bold text-cyan-800 bg-cyan-100 px-2 py-0.5 rounded-md">
                CCS2 &amp; Type-2 Fast
              </span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              {slots
                .filter(s => s.is_ev)
                .map(slot => renderSlot(slot))}
            </div>
          </div>
        )}
      </div>

      {/* Selected Slot Wayfinding Direction Banner */}
      {activeSlot && (
        <div className="p-4 bg-slate-900 text-white border-t border-slate-800 animate-in slide-in-from-bottom-2 duration-150">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div
                className={`w-12 h-12 rounded-xl flex items-center justify-center font-black text-lg border ${
                  activeSlot.status === 'AVAILABLE'
                    ? 'bg-emerald-600 border-emerald-400'
                    : activeSlot.status === 'OCCUPIED'
                    ? 'bg-rose-600 border-rose-400'
                    : 'bg-amber-500 border-amber-300'
                }`}
              >
                {activeSlot.slot_number}
              </div>

              <div>
                <div className="flex items-center gap-2">
                  <span className="font-bold text-sm text-white">
                    Bay {activeSlot.slot_number} ({activeSlot.type_name})
                  </span>
                  <span
                    className={`text-[10px] font-black px-2 py-0.5 rounded-md ${
                      activeSlot.status === 'AVAILABLE'
                        ? 'bg-emerald-200 text-emerald-950'
                        : activeSlot.status === 'OCCUPIED'
                        ? 'bg-rose-200 text-rose-950'
                        : 'bg-amber-200 text-amber-950'
                    }`}
                  >
                    {activeSlot.status}
                  </span>
                </div>

                {/* Location Landmark & Wayfinding Hint */}
                <div className="flex items-center gap-1.5 text-xs text-blue-200 mt-1">
                  <Navigation className="w-3.5 h-3.5 text-blue-400 shrink-0" />
                  <span>
                    <strong>Directions:</strong> {activeSlot.location_hint}
                  </span>
                </div>

                {/* ADMIN ONLY DETAILS (Completely hidden from customer!) */}
                {isAdmin && activeSlot.current_reg_no && (
                  <p className="text-xs text-amber-300 font-mono mt-1 bg-slate-800/80 px-2 py-0.5 rounded-md inline-block">
                    Occupant: {activeSlot.current_reg_no} ({activeSlot.current_owner_name} • 📞{' '}
                    {activeSlot.current_phone})
                  </p>
                )}
              </div>
            </div>

            <div className="flex items-center gap-2">
              {activeSlot.status === 'AVAILABLE' && onSelectSlot && (
                <button
                  onClick={() => {
                    onSelectSlot(activeSlot);
                    setActiveSlot(null);
                  }}
                  className="bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold px-4 py-2 rounded-xl shadow-md transition flex items-center gap-1.5"
                >
                  <span>Select &amp; Book Bay</span>
                  <ArrowRight className="w-4 h-4" />
                </button>
              )}
              <button
                onClick={() => setActiveSlot(null)}
                className="text-xs text-slate-400 hover:text-white px-3 py-1.5 rounded-lg hover:bg-slate-800 transition"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );

  function renderSlot(slot: ParkingSlot) {
    const isSelected = selectedSlotId === slot.slot_id;
    const isAvailable = slot.status === 'AVAILABLE';

    return (
      <div
        key={slot.slot_id}
        onClick={() => {
          setActiveSlot(slot);
          if (isAvailable && onSelectSlot) {
            onSelectSlot(slot);
          }
        }}
        className={`cursor-pointer p-3 rounded-xl border-2 transition-all duration-150 flex flex-col justify-between ${
          isSelected
            ? 'ring-4 ring-blue-500 border-blue-600 bg-blue-50 shadow-md scale-[1.02]'
            : 'bg-white border-slate-200 hover:border-slate-400 hover:shadow-xs'
        }`}
      >
        {/* Top: Slot number badge & icon */}
        <div className="flex items-center justify-between">
          <span className={`font-mono font-black text-xs px-2.5 py-1 rounded-md shadow-xs ${getStatusColor(slot)}`}>
            {slot.slot_number}
          </span>
          <div className="text-slate-400">
            {slot.is_ev ? (
              <Zap className="w-4 h-4 text-cyan-600" />
            ) : slot.type_name === 'Car' ? (
              <Car className="w-4 h-4" />
            ) : (
              <Bike className="w-4 h-4" />
            )}
          </div>
        </div>

        {/* Center: Privacy-guaranteed status */}
        <div className="my-2.5 min-h-[38px] flex flex-col justify-center text-center">
          {slot.status === 'AVAILABLE' ? (
            <div>
              <span className="text-[11px] font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-md inline-block">
                AVAILABLE
              </span>
              <span className="text-[10px] text-slate-400 block mt-0.5">Ready to park</span>
            </div>
          ) : slot.status === 'OCCUPIED' ? (
            <div>
              {/* ADMIN sees vehicle details; CUSTOMER sees ONLY OCCUPIED */}
              {isAdmin && slot.current_reg_no ? (
                <div>
                  <span className="font-mono font-bold text-xs text-slate-900 block truncate">
                    {slot.current_reg_no}
                  </span>
                  <span className="text-[10px] text-slate-500 truncate block">
                    {slot.current_owner_name}
                  </span>
                </div>
              ) : (
                <div>
                  <span className="text-[11px] font-bold text-rose-700 bg-rose-50 px-2 py-0.5 rounded-md inline-block">
                    OCCUPIED
                  </span>
                  <span className="text-[10px] text-slate-400 block mt-0.5">In Use</span>
                </div>
              )}
            </div>
          ) : (
            <div>
              {/* RESERVED */}
              <span className="text-[11px] font-bold text-amber-800 bg-amber-50 px-2 py-0.5 rounded-md inline-block">
                RESERVED
              </span>
              <span className="text-[10px] text-slate-400 block mt-0.5">Advance Booked</span>
            </div>
          )}
        </div>

        {/* Bottom: Location landmark hint preview */}
        <div className="pt-2 border-t border-slate-100 text-[10px] text-slate-400 truncate flex items-center gap-1">
          <MapPin className="w-3 h-3 text-slate-400 shrink-0" />
          <span className="truncate">{slot.zone}</span>
        </div>
      </div>
    );
  }
};
