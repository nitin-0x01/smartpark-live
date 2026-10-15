export type VehicleCategory = 'Car' | 'Bike' | 'EV Car';

export interface VehicleType {
  type_id: number;
  type_name: VehicleCategory;
  rate_per_hour: number;
}

export type SlotStatus = 'AVAILABLE' | 'OCCUPIED' | 'RESERVED' | 'MAINTENANCE';

export interface ParkingSlot {
  slot_id: number;
  slot_number: string;
  type_id: number;
  type_name: VehicleCategory;
  is_occupied: number; // 0 or 1
  status: SlotStatus;
  floor: string;
  zone: string;
  location_hint: string; // e.g. "Ground Floor - Wing A, Near Gate 1"
  is_ev: boolean;
  charging_power_kw?: number;
  current_reg_no?: string;
  current_owner_name?: string;
  current_phone?: string;
  entry_time?: string;
}

export interface Vehicle {
  vehicle_id: number;
  reg_no: string;
  owner_name: string;
  phone: string;
  type_id: number;
  user_id?: number;
}

export type TicketStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export interface Ticket {
  ticket_id: number;
  vehicle_id: number;
  reg_no: string;
  owner_name: string;
  phone: string;
  slot_id: number;
  slot_number: string;
  location_hint: string;
  type_id: number;
  type_name: VehicleCategory;
  entry_time: string;
  exit_time?: string;
  status: TicketStatus;
  qr_code: string;
  amount?: number;
  duration_minutes?: number;
  payment_mode?: 'UPI' | 'Cash' | 'Card';
}

export interface Payment {
  payment_id: number;
  ticket_id: number;
  reg_no: string;
  amount: number;
  pay_time: string;
  mode: 'UPI' | 'Cash' | 'Card';
  status: 'SUCCESS' | 'PENDING' | 'FAILED';
  transaction_ref: string;
}

export interface Reservation {
  reservation_id: number;
  user_id: number;
  customer_name: string;
  phone: string;
  reg_no: string;
  vehicle_type: VehicleCategory;
  slot_id: number;
  slot_number: string;
  location_hint: string;
  start_time: string;
  end_time: string;
  estimated_amount: number;
  status: 'CONFIRMED' | 'CHECKED_IN' | 'CANCELLED' | 'EXPIRED';
  created_at: string;
  qr_code: string;
}

export interface EVChargingSession {
  session_id: number;
  slot_id: number;
  slot_number: string;
  location_hint: string;
  reg_no: string;
  owner_name: string;
  start_time: string;
  end_time?: string;
  kwh_consumed: number;
  rate_per_kwh: number;
  total_cost: number;
  status: 'CHARGING' | 'COMPLETED';
}

export interface UserAccount {
  user_id: number;
  name: string;
  email: string;
  phone: string;
  role: 'ADMIN' | 'CUSTOMER';
  created_at: string;
}
