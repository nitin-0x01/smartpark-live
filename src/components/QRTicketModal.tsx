import React, { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { Ticket, Reservation } from '../types';
import { QrCode, Printer, X, CheckCircle, Car, Clock, ShieldCheck, MapPin } from 'lucide-react';

interface QRTicketModalProps {
  item: Ticket | Reservation;
  isReservation?: boolean;
  onClose: () => void;
}

export const QRTicketModal: React.FC<QRTicketModalProps> = ({ item, isReservation = false, onClose }) => {
  const [qrDataUrl, setQrDataUrl] = useState<string>('');

  const regNo = item.reg_no;
  const slotNo = item.slot_number;
  const id = isReservation ? (item as Reservation).reservation_id : (item as Ticket).ticket_id;
  const qrString = item.qr_code;

  useEffect(() => {
    QRCode.toDataURL(qrString, {
      width: 240,
      margin: 1,
      color: {
        dark: '#0f172a',
        light: '#ffffff'
      }
    })
      .then(url => setQrDataUrl(url))
      .catch(err => console.error(err));
  }, [qrString]);

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-200">
        {/* Header */}
        <div className="bg-slate-900 text-white p-5 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <QrCode className="w-6 h-6 text-blue-400" />
            <div>
              <h3 className="font-bold text-lg leading-tight">Digital Parking Pass</h3>
              <p className="text-xs text-slate-400">SmartPark Pro Entry / Exit Token</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Ticket Body */}
        <div className="p-6">
          <div className="border-2 border-dashed border-blue-200 bg-blue-50/50 rounded-xl p-5 text-center relative overflow-hidden">
            {/* Stamp Badge */}
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-300 mb-3">
              <CheckCircle className="w-3.5 h-3.5 text-emerald-600" />
              {isReservation ? 'VERIFIED RESERVATION' : 'ACTIVE PARKING TICKET'}
            </div>

            {/* QR Code Container */}
            <div className="bg-white p-3 rounded-xl shadow-xs inline-block mx-auto border border-slate-200 my-1">
              {qrDataUrl ? (
                <img src={qrDataUrl} alt="Parking QR Code" className="w-48 h-48 mx-auto" />
              ) : (
                <div className="w-48 h-48 flex items-center justify-center text-slate-400 text-sm">
                  Generating QR...
                </div>
              )}
            </div>

            <p className="text-xs font-mono text-slate-500 mt-2">
              Pass ID: #{id} • Scan at Entry &amp; Exit Boom Gate
            </p>

            {/* Details Grid */}
            <div className="grid grid-cols-2 gap-3 mt-4 text-left bg-white p-3.5 rounded-lg border border-slate-200">
              <div>
                <span className="text-[11px] text-slate-400 font-medium uppercase tracking-wider block">Vehicle No</span>
                <span className="font-mono font-bold text-slate-800 text-sm flex items-center gap-1">
                  <Car className="w-3.5 h-3.5 text-blue-600" />
                  {regNo}
                </span>
              </div>
              <div>
                <span className="text-[11px] text-slate-400 font-medium uppercase tracking-wider block">Assigned Slot</span>
                <span className="font-bold text-blue-700 text-base flex items-center gap-1">
                  <MapPin className="w-3.5 h-3.5 text-blue-600" />
                  Slot {slotNo}
                </span>
              </div>
              <div>
                <span className="text-[11px] text-slate-400 font-medium uppercase tracking-wider block">Customer Name</span>
                <span className="font-medium text-slate-700 text-xs">
                  {isReservation ? (item as Reservation).customer_name : (item as Ticket).owner_name}
                </span>
              </div>
              <div>
                <span className="text-[11px] text-slate-400 font-medium uppercase tracking-wider block">
                  {isReservation ? 'Booking Time' : 'Entry Time'}
                </span>
                <span className="font-medium text-slate-700 text-xs flex items-center gap-1">
                  <Clock className="w-3 h-3 text-slate-400" />
                  {new Date((item as any).entry_time || (item as any).start_time).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </span>
              </div>
            </div>

            <div className="flex items-center justify-center gap-1.5 mt-3 text-[11px] text-slate-500">
              <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" />
              Server cryptographically verified token
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex gap-3 mt-5">
            <button
              onClick={handlePrint}
              className="flex-1 flex items-center justify-center gap-2 bg-slate-900 hover:bg-slate-800 text-white py-2.5 px-4 rounded-xl font-medium text-sm transition shadow-sm"
            >
              <Printer className="w-4 h-4" />
              Print / Save PDF
            </button>
            <button
              onClick={onClose}
              className="px-5 py-2.5 border border-slate-300 text-slate-700 hover:bg-slate-100 rounded-xl font-medium text-sm transition"
            >
              Done
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
