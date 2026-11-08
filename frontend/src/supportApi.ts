import axios from "axios";
import { API_URL } from "./api";
import {
  AiConversation,
  AiMessage,
  Ticket,
  TicketCategory,
  TicketMessage,
} from "./types";

export async function getTickets() {
  const response = await axios.get<Ticket[]>(`${API_URL}/tickets`, {
    params: { _sort: "created_at", _order: "desc" },
  });
  return response.data;
}

export async function addTicket(ticket: Omit<Ticket, "id">) {
  const response = await axios.post<Ticket>(`${API_URL}/tickets`, ticket);
  return response.data;
}

export async function updateTicket(id: number, changes: Partial<Ticket>) {
  const response = await axios.patch<Ticket>(
    `${API_URL}/tickets/${id}`,
    changes,
  );
  return response.data;
}

export async function getTicketCategories() {
  const response = await axios.get<TicketCategory[]>(
    `${API_URL}/ticket_categories`,
  );
  return response.data;
}

export async function getTicketMessages(ticketId: number) {
  const response = await axios.get<TicketMessage[]>(
    `${API_URL}/ticket_messages`,
    { params: { ticket_id: ticketId } },
  );
  return response.data;
}

export async function addTicketMessage(message: Omit<TicketMessage, "id">) {
  const response = await axios.post<TicketMessage>(
    `${API_URL}/ticket_messages`,
    message,
  );
  return response.data;
}

export async function getConversations(userId: number, offeringId: number) {
  const response = await axios.get<AiConversation[]>(
    `${API_URL}/ai_conversations`,
    { params: { user_id: userId, offering_id: offeringId } },
  );
  return response.data;
}

export async function addConversation(
  conversation: Omit<AiConversation, "id">,
) {
  const response = await axios.post<AiConversation>(
    `${API_URL}/ai_conversations`,
    conversation,
  );
  return response.data;
}

export async function getAiMessages(conversationId: number) {
  const response = await axios.get<AiMessage[]>(`${API_URL}/ai_messages`, {
    params: { conversation_id: conversationId },
  });
  return response.data;
}

export async function addAiMessage(
  message: Omit<AiMessage, "id" | "model" | "token_count">,
) {
  // ASSUMPTION A19: there is no AI service yet, so model and token_count are filled with simple mock values.
  const response = await axios.post<AiMessage>(`${API_URL}/ai_messages`, {
    ...message,
    model: message.sender === "ai" ? "mock-assistant" : "",
    token_count: Math.ceil(message.content.length / 4),
  });
  return response.data;
}
