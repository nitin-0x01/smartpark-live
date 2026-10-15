import React, { useState, useEffect } from 'react';
import { DatabaseManager, DatabaseState } from './mockDb';
import { AdminDashboard } from './components/AdminDashboard';
import { CustomerDashboard } from './components/CustomerDashboard';
import { AdminRevenueAnalytics } from './components/AdminRevenueAnalytics';
import { fetchBackendState } from './backendApi';
import {
  Car,
  Compass,
  TrendingUp,
  QrCode,
  RotateCcw,
  CheckCircle2,
  Menu,
  X,
  ChevronRight,
  Shield,
  User,
  SlidersHorizontal,
  Building2
} from 'lucide-react';

export default function App() {
  const [dbState, setDbState] = useState<DatabaseState>(DatabaseManager.getState());
  const [currentRole, setCurrentRole] = useState<'ADMIN' | 'CUSTOMER'>('CUSTOMER');
  const [currentAdminNav, setCurrentAdminNav] = useState<'OPERATIONS' | 'ANALYTICS'>('OPERATIONS');
  const [mobileMenuOpen, setMobileMenuOpen] = useState<boolean>(false);
  const [notification, setNotification] = useState<string | null>(null);

  const refreshDb = async () => {
    try {
      setDbState(await fetchBackendState());
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Backend connection failed.');
    }
  };

  useEffect(() => {
    void refreshDb();
  }, []);

  const handleResetDb = () => {
    if (window.confirm('Reset parking bays and active tickets to initial default state?')) {
      DatabaseManager.resetToDefault();
      refreshDb();
      showToast('Facility reset to default 15-bay configuration!');
    }
  };

  const showToast = (msg: string) => {
    setNotification(msg);
    setTimeout(() => setNotification(null), 3500);
  };

  return (
    <div className="min-h-screen bg-slate-100 flex flex-col md:flex-row text-slate-800 font-sans">
      {/* Toast Notification */}
      {notification && (
        <div className="fixed top-4 right-4 z-50 bg-slate-900 text-white px-4 py-2.5 rounded-xl shadow-xl flex items-center gap-2 border border-slate-700 text-xs font-bold animate-in slide-in-from-top-2 duration-200">
          <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          <span>{notification}</span>
        </div>
      )}

      {/* Mobile Header */}
      <div className="md:hidden bg-slate-900 text-white p-4 flex items-center justify-between border-b border-slate-800">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center font-black text-white">
            P
          </div>
          <div>
            <span className="font-black text-sm tracking-tight block">SmartPark Live</span>
            <span className="text-[10px] text-slate-400">Automated Parking Facility</span>
          </div>
        </div>

        <button
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          className="p-1.5 rounded-lg bg-slate-800 text-slate-300 hover:text-white"
        >
          {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
      </div>

      {/* Navy Blue Sidebar */}
      <aside
        className={`${
          mobileMenuOpen ? 'block' : 'hidden'
        } md:block w-full md:w-64 bg-slate-900 text-white flex-shrink-0 flex flex-col justify-between border-r border-slate-800 z-40`}
      >
        <div>
          {/* Brand Logo */}
          <div className="p-5 border-b border-slate-800/80">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-500 flex items-center justify-center shadow-lg shadow-blue-500/20 font-black text-lg text-white">
                P
              </div>
              <div>
                <h1 className="font-black text-base tracking-tight leading-none text-white">SmartPark Live</h1>
                <span className="text-[10px] text-blue-400 font-semibold">15-Bay Intelligent Facility</span>
              </div>
            </div>

            {/* Role Switcher */}
            <div className="mt-4 p-1 bg-slate-800 rounded-xl border border-slate-700/60 flex items-center text-xs font-bold">
              <button
                onClick={() => {
                  setCurrentRole('CUSTOMER');
                  setMobileMenuOpen(false);
                }}
                className={`flex-1 py-1.5 rounded-lg transition text-center ${
                  currentRole === 'CUSTOMER'
                    ? 'bg-blue-600 text-white shadow-xs'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Customer App
              </button>
              <button
                onClick={() => {
                  setCurrentRole('ADMIN');
                  setMobileMenuOpen(false);
                }}
                className={`flex-1 py-1.5 rounded-lg transition text-center ${
                  currentRole === 'ADMIN'
                    ? 'bg-blue-600 text-white shadow-xs'
                    : 'text-slate-400 hover:text-white'
                }`}
              >
                Admin Console
              </button>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="p-4 space-y-1.5 text-xs font-medium">
            {currentRole === 'CUSTOMER' ? (
              <>
                <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider px-3 mb-2">
                  Driver Services
                </div>
                <div className="px-3 py-2 bg-blue-600/10 text-blue-400 rounded-xl font-bold flex items-center gap-2 border border-blue-500/20">
                  <Car className="w-4 h-4 text-blue-400" />
                  <span>Reserve Bay &amp; Passes</span>
                </div>
                <div className="p-3 text-[11px] text-slate-400 bg-slate-800/40 rounded-xl border border-slate-800 space-y-1">
                  <span className="font-bold text-slate-300 block">How it works:</span>
                  <p>1. Pick your vehicle &amp; open bay.</p>
                  <p>2. Get your instant QR pass.</p>
                  <p>3. Scan at boom barrier to enter.</p>
                </div>
              </>
            ) : (
              <>
                <div className="text-[10px] font-bold text-slate-400 uppercase tracking-wider px-3 mb-2">
                  Admin Facility Controls
                </div>

                <button
                  onClick={() => {
                    setCurrentAdminNav('OPERATIONS');
                    setMobileMenuOpen(false);
                  }}
                  className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-xl transition font-bold ${
                    currentAdminNav === 'OPERATIONS'
                      ? 'bg-blue-600 text-white shadow-xs'
                      : 'text-slate-300 hover:bg-slate-800/80 hover:text-white'
                  }`}
                >
                  <SlidersHorizontal className="w-4 h-4" />
                  <span>Gate Barrier &amp; 15 Bays</span>
                </button>

                <button
                  onClick={() => {
                    setCurrentAdminNav('ANALYTICS');
                    setMobileMenuOpen(false);
                  }}
                  className={`w-full flex items-center gap-2.5 px-3 py-2.5 rounded-xl transition font-bold ${
                    currentAdminNav === 'ANALYTICS'
                      ? 'bg-blue-600 text-white shadow-xs'
                      : 'text-slate-300 hover:bg-slate-800/80 hover:text-white'
                  }`}
                >
                  <TrendingUp className="w-4 h-4" />
                  <span>Financial Revenue &amp; CSV</span>
                </button>
              </>
            )}
          </nav>
        </div>

        {/* Bottom Profile & Reset Facility */}
        <div className="p-4 border-t border-slate-800/80 space-y-3">
          <div className="bg-slate-800/60 p-3 rounded-xl border border-slate-700/50 flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-full bg-blue-600 text-white flex items-center justify-center font-bold text-xs">
              {currentRole === 'ADMIN' ? 'A' : 'D'}
            </div>
            <div>
              <span className="text-xs font-bold text-white block">
                {currentRole === 'ADMIN' ? 'Facility Manager' : 'Driver Account'}
              </span>
              <span className="text-[10px] text-slate-400 font-medium">
                {currentRole === 'ADMIN' ? 'Full Audit Access' : 'Verified Driver'}
              </span>
            </div>
          </div>

          <button
            onClick={handleResetDb}
            className="w-full flex items-center justify-center gap-1.5 text-[11px] text-slate-400 hover:text-rose-400 py-2 rounded-lg hover:bg-rose-950/20 transition font-medium"
            title="Reset bays back to default"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span>Reset 15-Bay Database</span>
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        {/* Top Navbar */}
        <header className="bg-white border-b border-slate-200 px-6 py-4 flex flex-wrap items-center justify-between gap-4 sticky top-0 z-30 shadow-xs">
          <div className="flex items-center gap-2 text-xs text-slate-500">
            <span className="font-bold text-slate-900">
              {currentRole === 'ADMIN' ? 'Facility Command Tower' : 'Driver Parking Terminal'}
            </span>
            <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
            <span className="text-blue-600 font-bold">
              {currentRole === 'CUSTOMER'
                ? 'Advance Reservation & Live Floor Map'
                : currentAdminNav === 'OPERATIONS'
                ? 'Gate Boom Barrier & Live Slot Audit'
                : 'Revenue Analytics & Reports'}
            </span>
          </div>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 bg-slate-50 px-3.5 py-1.5 rounded-xl border border-slate-200 text-xs font-bold">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse" />
              <span className="text-slate-800">
                {dbState.slots.filter(s => s.status === 'AVAILABLE').length} Bays Free
              </span>
            </div>
          </div>
        </header>

        {/* Dynamic View Body */}
        <div className="p-4 sm:p-6 max-w-7xl w-full mx-auto space-y-6">
          {currentRole === 'CUSTOMER' ? (
            <CustomerDashboard dbState={dbState} onRefreshDb={refreshDb} />
          ) : currentAdminNav === 'OPERATIONS' ? (
            <AdminDashboard dbState={dbState} onRefreshDb={refreshDb} />
          ) : (
            <AdminRevenueAnalytics dbState={dbState} />
          )}
        </div>
      </main>
    </div>
  );
}
