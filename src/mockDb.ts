import {
  ParkingSlot,
  Ticket,
  Payment,
  Reservation,
  Vehicle,
  VehicleType,
  EVChargingSession,
  UserAccount,
  VehicleCategory
} from './types';

const STORAGE_KEY = 'smartpark_live_db_v3';

export interface DatabaseState {
  vehicleTypes: VehicleType[];
  slots: ParkingSlot[];
  vehicles: Vehicle[];
  tickets: Ticket[];
  payments: Payment[];
  reservations: Reservation[];
  evSessions: EVChargingSession[];
  users: UserAccount[];
  nextTicketId: number;
  nextPaymentId: number;
  nextReservationId: number;
  nextSessionId: number;
  nextVehicleId: number;
}

const getInitialState = (): DatabaseState => {
  const now = new Date();
  const twoHoursAgo = new Date(now.getTime() - 2 * 60 * 60 * 1000).toISOString();
  const oneHourAgo = new Date(now.getTime() - 1 * 60 * 60 * 1000).toISOString();
  const yesterday = new Date(now.getTime() - 24 * 60 * 60 * 1000).toISOString();
  const tomorrow = new Date(now.getTime() + 12 * 60 * 60 * 1000).toISOString();
  const tomorrowEnd = new Date(now.getTime() + 15 * 60 * 60 * 1000).toISOString();

  const vehicleTypes: VehicleType[] = [
    { type_id: 1, type_name: 'Car', rate_per_hour: 40.0 },
    { type_id: 2, type_name: 'Bike', rate_per_hour: 20.0 },
    { type_id: 3, type_name: 'EV Car', rate_per_hour: 50.0 }
  ];

  // 15 organized slots with exact wayfinding location hints
  const slots: ParkingSlot[] = [
    // 6 Car Slots (C1 to C6)
    {
      slot_id: 1,
      slot_number: 'C1',
      type_id: 1,
      type_name: 'Car',
      is_occupied: 1,
      status: 'OCCUPIED',
      floor: 'Ground Floor',
      zone: 'Zone A',
      location_hint: 'Ground Floor • Zone A, Turn left immediately after Entry Gate 1 (Pillar A1)',
      is_ev: false,
      current_reg_no: 'MH12AB1234',
      current_owner_name: 'Rahul Sharma',
      current_phone: '9876543210',
      entry_time: twoHoursAgo
    },
    {
      slot_id: 2,
      slot_number: 'C2',
      type_id: 1,
      type_name: 'Car',
      is_occupied: 0,
      status: 'AVAILABLE',
      floor: 'Ground Floor',
      zone: 'Zone A',
      location_hint: 'Ground Floor • Zone A, Straight ahead 20m from Gate 1, Near Elevator Lobby 1 (Pillar A2)',
      is_ev: false
    },
    {
      slot_id: 3,
      slot_number: 'C3',
      type_id: 1,
      type_name: 'Car',
      is_occupied: 1,
      status: 'RESERVED',
      floor: 'Ground Floor',
      zone: 'Zone A',
      location_hint: 'Ground Floor • Zone A, Near Main Staircase (Pillar A3)',
      is_ev: false,
      current_reg_no: 'DL01XY9988',
      current_owner_name: 'Priya Singh',
      current_phone: '9811223344'
    },
    {
      slot_id: 4,
      slot_number: 'C4',
      type_id: 1,
      type_name: 'Car',
      is_occupied: 0,
      status: 'AVAILABLE',
      floor: 'Ground Floor',
      zone: 'Zone A',
      location_hint: 'Ground Floor • Zone A, Center aisle opposite Payment Kiosk (Pillar A4)',
      is_ev: false
    },
    {
      slot_id: 5,
      slot_number: 'C5',
      type_id: 1,
      type_name: 'Car',
      is_occupied: 0,
      status: 'AVAILABLE',
      floor: 'First Floor (P1)',
      zone: 'Zone C',
      location_hint: 'Level P1 • Ramp up, Turn Right at Section C (Pillar C1)',
      is_ev: false
    },
    {
      slot_id: 6,
      slot_number: 'C6',
      type_id: 1,
      type_name: 'Car',
      is_occupied: 0,
      status: 'AVAILABLE',
      floor: 'First Floor (P1)',
      zone: 'Zone C',
      location_hint: 'Level P1 • Section C, Wide Bay near Exit Ramp (Pillar C2)',
      is_ev: false
    },

    // 6 Bike Slots (B1 to B6)
    {
      slot_id: 7,
      slot_number: 'B1',
      type_id: 2,
      type_name: 'Bike',
      is_occupied: 0,
      status: 'AVAILABLE',
      floor: 'Ground Floor',
      zone: 'Zone B',
      location_hint: 'Ground Floor • Two-Wheeler Bay B, Right side of Gate 1, Row 1 (Near Security Desk)',
      is_ev: false
    },
    {
      slot_id: 8,
      slot_number: 'B2',
      type_id: 2,
      type_name: 'Bike',
      is_occupied: 1,
      status: 'OCCUPIED',
      floor: 'Ground Floor',
      zone: 'Zone B',
      location_hint: 'Ground Floor • Two-Wheeler Bay B, Row 1, Center Stand',
      is_ev: false,
      current_reg_no: 'KA04MN5566',
      current_owner_name: 'Amit Verma',
      current_phone: '9123456789',
      entry_time: oneHourAgo
    },
    {
      slot_id: 9,
      slot_number: 'B3',
      type_id: 2,
      type_name: 'Bike',
      is_occupied: 0,
      status: 'AVAILABLE',
      floor: 'Ground Floor',
      zone: 'Zone B',
      location_hint: 'Ground Floor • Two-Wheeler Bay B, Row 1, Near Helmet Locker',
      is_ev: false
    },
    {
      slot_id: 10,
      slot_number: 'B4',
      type_id: 2,
      type_name: 'Bike',
      is_occupied: 0,
      status: 'AVAILABLE',
      floor: 'Ground Floor',
      zone: 'Zone B',
      location_hint: 'Ground Floor • Two-Wheeler Bay B, Row 2, Easy Exit Lane',
      is_ev: false
    },
    {
      slot_id: 11,
      slot_number: 'B5',
      type_id: 2,
      type_name: 'Bike',
      is_occupied: 0,
      status: 'AVAILABLE',
      floor: 'Ground Floor',
      zone: 'Zone B',
      location_hint: 'Ground Floor • Two-Wheeler Bay B, Row 2, Mid Bay',
      is_ev: false
    },
    {
      slot_id: 12,
      slot_number: 'B6',
      type_id: 2,
      type_name: 'Bike',
      is_occupied: 0,
      status: 'AVAILABLE',
      floor: 'Ground Floor',
      zone: 'Zone B',
      location_hint: 'Ground Floor • Two-Wheeler Bay B, Row 2, End Bay Near Exit Gate',
      is_ev: false
    },

    // 3 EV Fast Charging Bays (EV1 to EV3)
    {
      slot_id: 13,
      slot_number: 'EV1',
      type_id: 3,
      type_name: 'EV Car',
      is_occupied: 0,
      status: 'AVAILABLE',
      floor: 'Ground Floor',
      zone: 'EV Hub',
      location_hint: 'Ground Floor • EV Green Zone, Bay 1 (22kW Type-2 AC Fast Charger)',
      is_ev: true,
      charging_power_kw: 22
    },
    {
      slot_id: 14,
      slot_number: 'EV2',
      type_id: 3,
      type_name: 'EV Car',
      is_occupied: 0,
      status: 'AVAILABLE',
      floor: 'Ground Floor',
      zone: 'EV Hub',
      location_hint: 'Ground Floor • EV Green Zone, Bay 2 (50kW DC Supercharger with CCS2 Gun)',
      is_ev: true,
      charging_power_kw: 50
    },
    {
      slot_id: 15,
      slot_number: 'EV3',
      type_id: 3,
      type_name: 'EV Car',
      is_occupied: 0,
      status: 'AVAILABLE',
      floor: 'Ground Floor',
      zone: 'EV Hub',
      location_hint: 'Ground Floor • EV Green Zone, Bay 3 (22kW Dual Gun Charger)',
      is_ev: true,
      charging_power_kw: 22
    }
  ];

  const vehicles: Vehicle[] = [
    { vehicle_id: 1, reg_no: 'MH12AB1234', owner_name: 'Rahul Sharma', phone: '9876543210', type_id: 1, user_id: 2 },
    { vehicle_id: 2, reg_no: 'DL01XY9988', owner_name: 'Priya Singh', phone: '9811223344', type_id: 1, user_id: 3 },
    { vehicle_id: 3, reg_no: 'KA04MN5566', owner_name: 'Amit Verma', phone: '9123456789', type_id: 2, user_id: 4 }
  ];

  const tickets: Ticket[] = [
    {
      ticket_id: 101,
      vehicle_id: 1,
      reg_no: 'MH12AB1234',
      owner_name: 'Rahul Sharma',
      phone: '9876543210',
      slot_id: 1,
      slot_number: 'C1',
      location_hint: 'Ground Floor • Zone A, Turn left immediately after Entry Gate 1 (Pillar A1)',
      type_id: 1,
      type_name: 'Car',
      entry_time: twoHoursAgo,
      status: 'ACTIVE',
      qr_code: 'SMARTPARK|TKT|101|MH12AB1234|C1'
    },
    {
      ticket_id: 102,
      vehicle_id: 3,
      reg_no: 'KA04MN5566',
      owner_name: 'Amit Verma',
      phone: '9123456789',
      slot_id: 8,
      slot_number: 'B2',
      location_hint: 'Ground Floor • Two-Wheeler Bay B, Row 1, Center Stand',
      type_id: 2,
      type_name: 'Bike',
      entry_time: oneHourAgo,
      status: 'ACTIVE',
      qr_code: 'SMARTPARK|TKT|102|KA04MN5566|B2'
    }
  ];

  const payments: Payment[] = [
    {
      payment_id: 501,
      ticket_id: 98,
      reg_no: 'HR26DK4400',
      amount: 80.0,
      pay_time: new Date(now.getTime() - 4 * 60 * 60 * 1000).toISOString(),
      mode: 'UPI',
      status: 'SUCCESS',
      transaction_ref: 'UPI88921004'
    },
    {
      payment_id: 502,
      ticket_id: 99,
      reg_no: 'UP16ZZ1122',
      amount: 40.0,
      pay_time: new Date(now.getTime() - 8 * 60 * 60 * 1000).toISOString(),
      mode: 'Cash',
      status: 'SUCCESS',
      transaction_ref: 'CASH773910'
    }
  ];

  const reservations: Reservation[] = [
    {
      reservation_id: 301,
      user_id: 3,
      customer_name: 'Priya Singh',
      phone: '9811223344',
      reg_no: 'DL01XY9988',
      vehicle_type: 'Car',
      slot_id: 3,
      slot_number: 'C3',
      location_hint: 'Ground Floor • Zone A, Near Main Staircase (Pillar A3)',
      start_time: tomorrow,
      end_time: tomorrowEnd,
      estimated_amount: 120.0,
      status: 'CONFIRMED',
      created_at: now.toISOString(),
      qr_code: 'SMARTPARK|RESV|301|DL01XY9988|C3'
    }
  ];

  const evSessions: EVChargingSession[] = [];

  const users: UserAccount[] = [
    {
      user_id: 1,
      name: 'Nitin Kumar (Admin)',
      email: 'admin@smartpark.com',
      phone: '9999988888',
      role: 'ADMIN',
      created_at: yesterday
    },
    {
      user_id: 2,
      name: 'Rahul Sharma',
      email: 'rahul@gmail.com',
      phone: '9876543210',
      role: 'CUSTOMER',
      created_at: yesterday
    }
  ];

  return {
    vehicleTypes,
    slots,
    vehicles,
    tickets,
    payments,
    reservations,
    evSessions,
    users,
    nextTicketId: 103,
    nextPaymentId: 503,
    nextReservationId: 302,
    nextSessionId: 701,
    nextVehicleId: 4
  };
};

export class DatabaseManager {
  private static loadState(): DatabaseState {
    try {
      const data = localStorage.getItem(STORAGE_KEY);
      if (data) {
        return JSON.parse(data);
      }
    } catch {
      // fallback
    }
    const init = getInitialState();
    localStorage.setItem(STORAGE_KEY, JSON.stringify(init));
    return init;
  }

  private static saveState(state: DatabaseState): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }

  public static resetToDefault(): DatabaseState {
    const fresh = getInitialState();
    localStorage.setItem(STORAGE_KEY, JSON.stringify(fresh));
    return fresh;
  }

  public static getState(): DatabaseState {
    return this.loadState();
  }

  // ---------- CUSTOMER ADVANCE BOOKING ----------
  public static createReservation(
    customerName: string,
    phone: string,
    regNo: string,
    vehicleType: VehicleCategory,
    slotId: number
  ): { success: boolean; reservation?: Reservation; message: string } {
    const state = this.loadState();
    const cleanReg = regNo.trim().toUpperCase();
    const cleanPhone = phone.trim() || '9876543210';
    const cleanName = customerName.trim() || 'Customer';

    // 1. Check if vehicle is already parked or booked
    const alreadyActive = state.tickets.find(
      t => t.reg_no.toUpperCase() === cleanReg && t.status === 'ACTIVE'
    );
    if (alreadyActive) {
      return { success: false, message: `Vehicle ${cleanReg} is already parked in Slot ${alreadyActive.slot_number}!` };
    }

    const alreadyReserved = state.reservations.find(
      r => r.reg_no.toUpperCase() === cleanReg && r.status === 'CONFIRMED'
    );
    if (alreadyReserved) {
      return { success: false, message: `Vehicle ${cleanReg} already has an active reservation for Slot ${alreadyReserved.slot_number}!` };
    }

    // 2. Slot check
    const slot = state.slots.find(s => s.slot_id === slotId);
    if (!slot) return { success: false, message: 'Invalid slot selected!' };
    if (slot.status !== 'AVAILABLE') return { success: false, message: `Slot ${slot.slot_number} is already ${slot.status.toLowerCase()}!` };

    // 3. Rate calculation (2 hours default reservation window)
    const vType = state.vehicleTypes.find(t => t.type_name === vehicleType) || { rate_per_hour: 40 };
    const estimatedAmount = 2 * vType.rate_per_hour;

    const resId = state.nextReservationId++;
    const now = new Date();
    const end = new Date(now.getTime() + 2 * 60 * 60 * 1000);

    const reservation: Reservation = {
      reservation_id: resId,
      user_id: 2,
      customer_name: cleanName,
      phone: cleanPhone,
      reg_no: cleanReg,
      vehicle_type: vehicleType,
      slot_id: slot.slot_id,
      slot_number: slot.slot_number,
      location_hint: slot.location_hint,
      start_time: now.toISOString(),
      end_time: end.toISOString(),
      estimated_amount: estimatedAmount,
      status: 'CONFIRMED',
      created_at: now.toISOString(),
      qr_code: `SMARTPARK|RESV|${resId}|${cleanReg}|${slot.slot_number}`
    };

    // Mark slot as reserved
    slot.status = 'RESERVED';
    slot.is_occupied = 1;
    slot.current_reg_no = cleanReg;
    slot.current_owner_name = cleanName;
    slot.current_phone = cleanPhone;

    state.reservations.unshift(reservation);
    this.saveState(state);
    return {
      success: true,
      reservation,
      message: `Reservation confirmed for ${cleanReg} in Slot ${slot.slot_number}!`
    };
  }

  // ---------- SMART QR BARRIER: CHECK-IN (ENTRY) & CHECK-OUT (EXIT) ----------
  public static processQrScan(qrString: string): {
    action: 'CHECK_IN' | 'CHECK_OUT' | 'INVALID';
    success: boolean;
    ticket?: Ticket;
    reservation?: Reservation;
    amount?: number;
    hours?: number;
    message: string;
  } {
    const state = this.loadState();
    const token = qrString.trim();

    // 1. Is this an advance reservation checking in at Entry Gate?
    const reservation = state.reservations.find(
      r => (r.qr_code === token || token.includes(`RESV|${r.reservation_id}`)) && r.status === 'CONFIRMED'
    );

    if (reservation) {
      // Convert Reservation to Active Ticket at Entry Gate!
      reservation.status = 'CHECKED_IN';
      const slot = state.slots.find(s => s.slot_id === reservation.slot_id);
      if (slot) {
        slot.status = 'OCCUPIED';
        slot.is_occupied = 1;
        slot.entry_time = new Date().toISOString();
      }

      const ticketId = state.nextTicketId++;
      const nowISO = new Date().toISOString();
      const vType = state.vehicleTypes.find(t => t.type_name === reservation.vehicle_type) || state.vehicleTypes[0];

      const ticket: Ticket = {
        ticket_id: ticketId,
        vehicle_id: reservation.reservation_id,
        reg_no: reservation.reg_no,
        owner_name: reservation.customer_name,
        phone: reservation.phone,
        slot_id: reservation.slot_id,
        slot_number: reservation.slot_number,
        location_hint: reservation.location_hint,
        type_id: vType.type_id,
        type_name: reservation.vehicle_type,
        entry_time: nowISO,
        status: 'ACTIVE',
        qr_code: `SMARTPARK|TKT|${ticketId}|${reservation.reg_no}|${reservation.slot_number}`
      };
      state.tickets.unshift(ticket);
      this.saveState(state);

      return {
        action: 'CHECK_IN',
        success: true,
        ticket,
        reservation,
        message: `VALID RESERVATION! Checked In: ${reservation.reg_no} -> Park at Slot ${reservation.slot_number}. Gate Opened!`
      };
    }

    // 2. Is this an active ticket checking out at Exit Gate?
    const activeTicket = state.tickets.find(
      t =>
        (t.qr_code === token ||
          token.includes(`TKT|${t.ticket_id}`) ||
          token.toUpperCase() === t.reg_no.toUpperCase() ||
          t.ticket_id.toString() === token) &&
        t.status === 'ACTIVE'
    );

    if (activeTicket) {
      // Calculate bill
      const entryDate = new Date(activeTicket.entry_time);
      const now = new Date();
      const durationMin = Math.max(1, Math.round((now.getTime() - entryDate.getTime()) / (1000 * 60)));
      const hours = Math.max(1, Math.ceil(durationMin / 60));
      const typeObj = state.vehicleTypes.find(t => t.type_id === activeTicket.type_id) || { rate_per_hour: 40 };
      const amount = hours * typeObj.rate_per_hour;

      return {
        action: 'CHECK_OUT',
        success: true,
        ticket: activeTicket,
        amount,
        hours,
        message: `TICKET VERIFIED: ${activeTicket.reg_no} (Slot ${activeTicket.slot_number}). Duration: ${hours} hr. Total: ₹${amount.toFixed(2)}`
      };
    }

    return {
      action: 'INVALID',
      success: false,
      message: `Invalid or expired QR token / Ticket: "${token}".`
    };
  }

  // ---------- MANUAL ENTRY (FOR WALK-IN VEHICLES WITHOUT PRE-BOOKING) ----------
  public static manualParkVehicle(
    regNo: string,
    ownerName: string,
    phone: string,
    typeId: number,
    slotId: number
  ): { success: boolean; ticket?: Ticket; message: string } {
    const state = this.loadState();
    const cleanReg = regNo.trim().toUpperCase();

    // Check if vehicle already parked
    const alreadyParked = state.tickets.find(
      t => t.reg_no.toUpperCase() === cleanReg && t.status === 'ACTIVE'
    );
    if (alreadyParked) {
      return { success: false, message: `Vehicle ${cleanReg} is already parked in Slot ${alreadyParked.slot_number}!` };
    }

    const slot = state.slots.find(s => s.slot_id === slotId);
    if (!slot) return { success: false, message: 'Invalid slot selected!' };
    if (slot.status !== 'AVAILABLE') return { success: false, message: `Slot ${slot.slot_number} is already occupied!` };

    const typeObj = state.vehicleTypes.find(t => t.type_id === typeId) || state.vehicleTypes[0];
    const ticketId = state.nextTicketId++;
    const nowISO = new Date().toISOString();

    const ticket: Ticket = {
      ticket_id: ticketId,
      vehicle_id: state.nextVehicleId++,
      reg_no: cleanReg,
      owner_name: ownerName.trim() || 'Walk-in Driver',
      phone: phone.trim() || 'N/A',
      slot_id: slot.slot_id,
      slot_number: slot.slot_number,
      location_hint: slot.location_hint,
      type_id: typeId,
      type_name: typeObj.type_name,
      entry_time: nowISO,
      status: 'ACTIVE',
      qr_code: `SMARTPARK|TKT|${ticketId}|${cleanReg}|${slot.slot_number}`
    };

    slot.status = 'OCCUPIED';
    slot.is_occupied = 1;
    slot.current_reg_no = cleanReg;
    slot.current_owner_name = ticket.owner_name;
    slot.current_phone = ticket.phone;
    slot.entry_time = nowISO;

    state.tickets.unshift(ticket);
    this.saveState(state);
    return {
      success: true,
      ticket,
      message: `Vehicle ${cleanReg} checked in manually! Assigned Slot: ${slot.slot_number}. Ticket #${ticketId}`
    };
  }

  // ---------- COMPLETE EXIT & PAYMENT (GATE CHECKOUT) ----------
  public static completeExitAndPay(
    ticketId: number,
    paymentMode: 'UPI' | 'Cash' | 'Card',
    customAmount?: number
  ): { success: boolean; payment?: Payment; message: string } {
    const state = this.loadState();
    const ticket = state.tickets.find(t => t.ticket_id === ticketId && t.status === 'ACTIVE');
    if (!ticket) return { success: false, message: 'Active ticket not found!' };

    const typeObj = state.vehicleTypes.find(t => t.type_id === ticket.type_id) || { rate_per_hour: 40 };
    const entryDate = new Date(ticket.entry_time);
    const now = new Date();
    const durationMin = Math.max(1, Math.round((now.getTime() - entryDate.getTime()) / (1000 * 60)));
    const hours = Math.max(1, Math.ceil(durationMin / 60));
    const amount = customAmount ?? hours * typeObj.rate_per_hour;

    // 1. Complete ticket
    ticket.exit_time = now.toISOString();
    ticket.status = 'COMPLETED';
    ticket.amount = amount;
    ticket.duration_minutes = durationMin;
    ticket.payment_mode = paymentMode;

    // 2. Free up slot in database
    const slot = state.slots.find(s => s.slot_id === ticket.slot_id);
    if (slot) {
      slot.status = 'AVAILABLE';
      slot.is_occupied = 0;
      slot.current_reg_no = undefined;
      slot.current_owner_name = undefined;
      slot.current_phone = undefined;
      slot.entry_time = undefined;
    }

    // 3. Record payment
    const paymentId = state.nextPaymentId++;
    const payment: Payment = {
      payment_id: paymentId,
      ticket_id: ticket.ticket_id,
      reg_no: ticket.reg_no,
      amount,
      pay_time: now.toISOString(),
      mode: paymentMode,
      status: 'SUCCESS',
      transaction_ref: `${paymentMode.toUpperCase()}${Date.now().toString().slice(-8)}`
    };
    state.payments.unshift(payment);

    this.saveState(state);
    return {
      success: true,
      payment,
      message: `Checkout complete for ${ticket.reg_no}! Fare: ₹${amount.toFixed(2)} received via ${paymentMode}. Slot ${ticket.slot_number} is now Available.`
    };
  }

  // ---------- CANCEL RESERVATION ----------
  public static cancelReservation(resId: number): boolean {
    const state = this.loadState();
    const res = state.reservations.find(r => r.reservation_id === resId);
    if (!res) return false;

    res.status = 'CANCELLED';
    const slot = state.slots.find(s => s.slot_id === res.slot_id);
    if (slot && slot.status === 'RESERVED') {
      slot.status = 'AVAILABLE';
      slot.is_occupied = 0;
      slot.current_reg_no = undefined;
      slot.current_owner_name = undefined;
      slot.current_phone = undefined;
    }

    this.saveState(state);
    return true;
  }
}
