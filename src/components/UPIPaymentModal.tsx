import React, { useState, useEffect } from 'react';
import QRCode from 'qrcode';
import { Smartphone, CheckCircle, AlertCircle, X, Shield, ArrowRight, Loader2 } from 'lucide-react';

interface UPIPaymentModalProps {
  amount: number;
  regNo: string;
  ticketIdOrDescription: string;
  onPaymentSuccess: (transactionRef: string, mode: 'UPI') => void;
  onClose: () => void;
}

export const UPIPaymentModal: React.FC<UPIPaymentModalProps> = ({
  amount,
  regNo,
  ticketIdOrDescription,
  onPaymentSuccess,
  onClose
}) => {
  const [upiQrUrl, setUpiQrUrl] = useState<string>('');
  const [selectedApp, setSelectedApp] = useState<'GPAY' | 'PHONEPE' | 'PAYTM' | 'BHIM'>('GPAY');
  const [isProcessing, setIsProcessing] = useState<boolean>(false);
  const [customUpiId, setCustomUpiId] = useState<string>('');
  const [errorMessage, setErrorMessage] = useState<string>('');

  const upiIntentString = `upi://pay?pa=smartpark.system@icici&pn=SmartPark_Pro&am=${amount.toFixed(2)}&cu=INR&tn=Parking_Fee_${regNo}`;

  useEffect(() => {
    QRCode.toDataURL(upiIntentString, {
      width: 200,
      margin: 1,
      color: {
        dark: '#0f172a',
        light: '#ffffff'
      }
    })
      .then(url => setUpiQrUrl(url))
      .catch(err => console.error(err));
  }, [upiIntentString, amount, regNo]);

  const handleSimulatePayment = (success: boolean) => {
    setIsProcessing(true);
    setErrorMessage('');

    setTimeout(() => {
      setIsProcessing(false);
      if (success) {
        const mockTxnId = `UPI${Date.now().toString().slice(-8)}${Math.floor(100 + Math.random() * 900)}`;
        onPaymentSuccess(mockTxnId, 'UPI');
      } else {
        setErrorMessage('UPI Bank Server timed out. Please retry or choose another payment method.');
      }
    }, 1200);
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-md w-full overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-200">
        {/* Header */}
        <div className="bg-gradient-to-r from-blue-900 via-indigo-900 to-slate-900 text-white p-5 flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <div className="bg-blue-600/30 p-2 rounded-xl border border-blue-400/30">
              <Smartphone className="w-5 h-5 text-blue-300" />
            </div>
            <div>
              <h3 className="font-bold text-lg">UPI Fast Checkout</h3>
              <p className="text-xs text-blue-200">Instant Automated Parking Payment</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-white/10 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Amount bar */}
        <div className="bg-slate-50 border-b border-slate-200 px-6 py-4 flex items-center justify-between">
          <div>
            <span className="text-xs text-slate-500 font-medium uppercase tracking-wider block">Total Payable</span>
            <span className="text-2xl font-black text-slate-900">₹{amount.toFixed(2)}</span>
          </div>
          <div className="text-right">
            <span className="text-xs text-slate-500 font-medium uppercase tracking-wider block">Vehicle</span>
            <span className="font-mono font-bold text-blue-800 text-sm">{regNo}</span>
            <span className="block text-[11px] text-slate-400">{ticketIdOrDescription}</span>
          </div>
        </div>

        {/* Modal Content */}
        <div className="p-6 space-y-5">
          {errorMessage && (
            <div className="p-3 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 text-red-500" />
              <span>{errorMessage}</span>
            </div>
          )}

          {/* QR Code Section */}
          <div className="border border-slate-200 rounded-xl p-4 text-center bg-white shadow-xs">
            <p className="text-xs font-semibold text-slate-700 mb-2">Scan &amp; Pay with Any UPI App</p>
            <div className="bg-slate-50 p-2 rounded-lg inline-block border border-slate-200">
              {upiQrUrl ? (
                <img src={upiQrUrl} alt="UPI QR" className="w-44 h-44 mx-auto" />
              ) : (
                <div className="w-44 h-44 flex items-center justify-center text-slate-400 text-xs">
                  Generating UPI QR...
                </div>
              )}
            </div>
            <p className="text-[11px] font-mono text-slate-500 mt-2">
              UPI ID: <span className="font-semibold text-slate-700">smartpark.system@icici</span>
            </p>
          </div>

          {/* Quick UPI App Selector */}
          <div>
            <label className="text-xs font-semibold text-slate-600 block mb-2">Or Pay via UPI Apps</label>
            <div className="grid grid-cols-4 gap-2">
              {[
                { id: 'GPAY', name: 'GPay', color: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
                { id: 'PHONEPE', name: 'PhonePe', color: 'bg-purple-50 text-purple-700 border-purple-200' },
                { id: 'PAYTM', name: 'Paytm', color: 'bg-cyan-50 text-cyan-700 border-cyan-200' },
                { id: 'BHIM', name: 'BHIM UPI', color: 'bg-amber-50 text-amber-700 border-amber-200' }
              ].map(app => (
                <button
                  key={app.id}
                  type="button"
                  onClick={() => setSelectedApp(app.id as any)}
                  className={`py-2 px-1 text-center rounded-lg border text-xs font-semibold transition ${
                    selectedApp === app.id ? `${app.color} ring-2 ring-blue-500 shadow-xs` : 'bg-slate-50 border-slate-200 text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  {app.name}
                </button>
              ))}
            </div>
          </div>

          {/* Simulated UPI Intent Trigger */}
          <div className="space-y-2 pt-2">
            <button
              onClick={() => handleSimulatePayment(true)}
              disabled={isProcessing}
              className="w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold py-3 px-4 rounded-xl shadow-md transition disabled:opacity-60"
            >
              {isProcessing ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Verifying with Bank Gateway...
                </>
              ) : (
                <>
                  <span>Pay ₹{amount.toFixed(2)} via {selectedApp}</span>
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>

            {/* Test Simulation Controls */}
            <div className="flex items-center justify-between text-[11px] text-slate-400 px-1 pt-1">
              <span className="flex items-center gap-1">
                <Shield className="w-3 h-3 text-emerald-600" />
                256-Bit Encrypted Sandbox
              </span>
              <button
                type="button"
                onClick={() => handleSimulatePayment(false)}
                className="text-slate-400 hover:text-red-500 transition underline underline-offset-2"
              >
                Test Failure Case
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
