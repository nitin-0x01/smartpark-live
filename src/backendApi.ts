import { DatabaseState } from './mockDb';
import { Reservation, Ticket, VehicleCategory } from './types';

const API_BASE = import.meta.env.VITE_BACKEND_URL || 'http://localhost:8080';

const numberValue = (value: string | number | undefined): number => Number(value || 0);

const normalizeTicket = (raw: Record<string, string | number>): Ticket => {
  const ticketId = numberValue(raw.ticketId);
  const regNo = String(raw.regNo || '');
  const slotNumber = String(raw.slotNumber || '');
  return {
    ticket_id: ticketId,
    vehicle_id: numberValue(raw.vehicleId),
    reg_no: regNo,
    owner_name: String(raw.ownerName || ''),
    phone: String(raw.phone || ''),
    slot_id: numberValue(raw.slotId),
    slot_number: slotNumber,
    location_hint: String(raw.locationHint || ''),
    type_id: numberValue(raw.typeId),
    type_name: raw.typeName as VehicleCategory,
    entry_time: String(raw.entryTime || ''),
    exit_time: raw.exitTime ? String(raw.exitTime) : undefined,
    status: raw.status as Ticket['status'],
    qr_code: `SMARTPARK|TKT|${ticketId}|${regNo}|${slotNumber}`
  };
};

export async function fetchBackendState(): Promise<DatabaseState> {
  const response = await fetch(`${API_BASE}/api/state`);
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.message || 'Backend state request failed.');

  return {
    ...payload,
    vehicleTypes: payload.vehicleTypes.map((type: Record<string, string>) => ({
      type_id: numberValue(type.typeId),
      type_name: type.typeName as VehicleCategory,
      rate_per_hour: numberValue(type.ratePerHour)
    })),
    slots: payload.slots.map((slot: Record<string, string>) => ({
      slot_id: numberValue(slot.slotId),
      slot_number: slot.slotNumber,
      type_id: numberValue(slot.typeId),
      type_name: slot.typeName as VehicleCategory,
      is_occupied: numberValue(slot.isOccupied),
      status: slot.status,
      floor: 'Ground Floor',
      zone: '',
      location_hint: slot.locationHint,
      is_ev: slot.typeName === 'EV Car'
    })),
    tickets: payload.tickets.map(normalizeTicket),
    payments: payload.payments.map((payment: Record<string, string>) => ({
      payment_id: numberValue(payment.paymentId),
      ticket_id: numberValue(payment.ticketId),
      reg_no: payment.regNo,
      amount: numberValue(payment.amount),
      pay_time: payment.payTime,
      mode: payment.mode,
      status: payment.status,
      transaction_ref: payment.transactionRef
    }))
  };
}

async function post<T>(path: string, data: Record<string, string | number>): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(
      Object.entries(data).map(([key, value]) => [key, String(value)])
    )
  });
  const payload = await response.json();
  if (!response.ok || payload.success === false) {
    throw new Error(payload.message || 'Backend request failed.');
  }
  return payload as T;
}

export async function createBackendBooking(
  customerName: string,
  phone: string,
  regNo: string,
  vehicleType: VehicleCategory,
  slotId: number
): Promise<{ ticket: Ticket; reservation: Reservation; message: string }> {
  const typeId = vehicleType === 'Car' ? 1 : vehicleType === 'Bike' ? 2 : 3;
  const result = await post<{ ticket: Record<string, string | number>; message: string }>('/api/book', {
    owner: customerName,
    phone,
    reg: regNo,
    typeId,
    slotId
  });
  const ticket = normalizeTicket(result.ticket);
  const reservation: Reservation = {
    reservation_id: ticket.ticket_id,
    user_id: 0,
    customer_name: ticket.owner_name,
    phone: ticket.phone,
    reg_no: ticket.reg_no,
    vehicle_type: ticket.type_name,
    slot_id: ticket.slot_id,
    slot_number: ticket.slot_number,
    location_hint: ticket.location_hint,
    start_time: ticket.entry_time,
    end_time: ticket.entry_time,
    estimated_amount: 0,
    status: 'CHECKED_IN',
    created_at: ticket.entry_time,
    qr_code: ticket.qr_code
  };
  return { ticket, reservation, message: result.message };
}

export async function scanBackendTicket(qr: string) {
  const result = await post<{
    action: 'CHECK_IN' | 'CHECK_OUT' | 'INVALID';
    success: boolean;
    ticket?: Record<string, string | number>;
    amount?: number;
    hours?: number;
    message: string;
  }>('/api/scan', { qr });
  return {
    ...result,
    ticket: result.ticket ? normalizeTicket(result.ticket) : undefined
  };
}

export async function parkBackendVehicle(
  reg: string,
  owner: string,
  phone: string,
  typeId: number
) {
  const result = await post<{ ticket: Record<string, string | number>; message: string }>('/api/park', {
    reg,
    owner,
    phone,
    typeId
  });
  return { ...result, ticket: normalizeTicket(result.ticket) };
}

export async function exitBackendVehicle(reg: string, mode: 'UPI' | 'Cash' | 'Card') {
  return post<{ amount: number; message: string }>('/api/exit', { reg, mode });
}
