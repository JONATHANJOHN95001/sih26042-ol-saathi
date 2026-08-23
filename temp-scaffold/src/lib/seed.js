// src/lib/seed.js
// Static seed data — no side effects, pure data

export const SEED_CAMPS = [
  {
    id: "camp-1",
    name: "Shantiniketan Relief Camp",
    ward: "Ward 12 – Rajarhat",
    capacity: 120,
    occupied: 45,
    supplies: { food: 82, water: 74, medical: 60 },
  },
  {
    id: "camp-2",
    name: "Flood Relief Centre Alpha",
    ward: "Ward 7 – Beliaghata",
    capacity: 80,
    occupied: 78,
    supplies: { food: 20, water: 35, medical: 55 },
  },
  {
    id: "camp-3",
    name: "Durgapur Community Shelter",
    ward: "Ward 3 – Asansol North",
    capacity: 200,
    occupied: 90,
    supplies: { food: 70, water: 88, medical: 77 },
  },
  {
    id: "camp-4",
    name: "Sunderban Aid Station",
    ward: "Ward 19 – Canning South",
    capacity: 60,
    occupied: 10,
    supplies: { food: 95, water: 91, medical: 89 },
  },
  {
    id: "camp-5",
    name: "Howrah Emergency Hub",
    ward: "Ward 5 – Liluah",
    capacity: 150,
    occupied: 130,
    supplies: { food: 30, water: 44, medical: 22 },
  },
  {
    id: "camp-6",
    name: "Midnapore Relief Village",
    ward: "Ward 8 – Paschim Medinipur",
    capacity: 100,
    occupied: 25,
    supplies: { food: 88, water: 80, medical: 93 },
  },
];

export const SEED_REQUESTS = [
  {
    id: "req-1",
    familyName: "Mondal Family",
    familySize: 5,
    needs: ["food", "medical"],
    urgency: 5,
    location: "Beliaghata Flood Zone",
  },
  {
    id: "req-2",
    familyName: "Biswas Household",
    familySize: 3,
    needs: ["water"],
    urgency: 2,
    location: "Rajarhat Lowlands",
  },
  {
    id: "req-3",
    familyName: "Chatterjee Group",
    familySize: 8,
    needs: ["food", "water", "medical"],
    urgency: 4,
    location: "Canning River Bank",
  },
  {
    id: "req-4",
    familyName: "Das Family",
    familySize: 2,
    needs: ["medical"],
    urgency: 3,
    location: "Liluah Industrial Area",
  },
  {
    id: "req-5",
    familyName: "Halder Extended Family",
    familySize: 11,
    needs: ["food", "water"],
    urgency: 5,
    location: "Paschim Medinipur Rural",
  },
];
