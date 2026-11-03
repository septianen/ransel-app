#!/usr/bin/env python3
"""Creates one complete sample trip for a store reviewer account, through the public API.

The account must already exist and be verified: add it to the SEED_USERS secret (deploy/README.md, "Store review
account"). The trip gets two members without an account, an itinerary over three days, packing items, shared
expenses and an open poll with the reviewer's vote, so every tab has something to show.

    python3 deploy/seed-demo-trip.py --api https://api.ransel.app --email reviewer@example.com

The password is asked for (or read from RANSEL_SEED_PASSWORD), never passed on the command line. Running it again
does nothing while the account has a trip with that title; --replace deletes it and makes a new one with fresh
dates (three weeks from today).
Standard library only.
"""
import argparse
import datetime as dt
import getpass
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

NAMESPACE = uuid.UUID("6f1c2a52-4d1e-4b8a-9a77-2f0c6c1e9d31")


class ApiError(Exception):
    def __init__(self, status, body):
        super().__init__(f"HTTP {status}: {body}")
        self.status = status


class Api:
    def __init__(self, origin):
        self.base = origin.rstrip("/") + "/api/v1"
        self.token = None

    def call(self, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, method=method)
        request.add_header("Accept", "application/json")
        if data is not None:
            request.add_header("Content-Type", "application/json")
        if self.token:
            request.add_header("Authorization", "Bearer " + self.token)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                raw = response.read()
        except urllib.error.HTTPError as e:
            raise ApiError(e.code, e.read().decode(errors="replace")) from None
        return json.loads(raw)["data"] if raw else None


def new_id(*parts):
    return str(uuid.uuid5(NAMESPACE, "/".join(parts)))


def instant(day, hour=12, minute=0):
    """A UTC timestamp for a local WIB time (UTC+7)."""
    t = dt.datetime.combine(day, dt.time(hour, minute)) - dt.timedelta(hours=7)
    return t.strftime("%Y-%m-%dT%H:%M:%SZ")


def even_split(amount, member_ids):
    share, rest = divmod(amount, len(member_ids))
    return [{"memberId": m, "shareMinor": share + (1 if i < rest else 0)} for i, m in enumerate(member_ids)]


TITLE = "Liburan Jogja"


def seed(api):
    trip_id = str(uuid.uuid4())
    start = dt.date.today() + dt.timedelta(days=21)
    days = [start + dt.timedelta(days=i) for i in range(3)]

    api.call("POST", "/trips", {
        "id": trip_id,
        "title": TITLE,
        "destination": "Yogyakarta, Indonesia",
        "startDate": days[0].isoformat(),
        "endDate": days[-1].isoformat(),
        "baseCurrency": "IDR",
        "budgetMinor": 4_500_000,
        "countryCode": "ID",
        "localCurrency": "IDR",
        "timeZone": "Asia/Jakarta",
    })

    for name in ("Rina", "Bima"):
        api.call("POST", f"/trips/{trip_id}/members", {"id": new_id(trip_id, "member", name), "displayName": name})
    members = api.call("GET", f"/trips/{trip_id}/members")
    me = next(m["memberId"] for m in members if m.get("userId"))
    friends = [m["memberId"] for m in members if not m.get("userId")]
    everyone = [me] + friends

    itinerary = [
        (0, "10:00", 60, "Check-in penginapan", "LODGING", "Prawirotaman"),
        (0, "13:00", 90, "Makan siang gudeg", "FOOD", "Gudeg Yu Djum"),
        (0, "16:00", 120, "Jalan sore di Malioboro", "SHOPPING", "Jalan Malioboro"),
        (1, "04:30", 240, "Sunrise di Borobudur", "ATTRACTION", "Candi Borobudur"),
        (1, "12:30", 60, "Makan siang", "FOOD", None),
        (1, "17:00", 120, "Sunset di Candi Prambanan", "ATTRACTION", "Candi Prambanan"),
        (2, "08:00", 90, "Keraton Yogyakarta", "ATTRACTION", "Keraton Ngayogyakarta Hadiningrat"),
        (2, "14:00", 45, "Kereta pulang", "TRANSPORT", "Stasiun Yogyakarta"),
    ]
    for i, (day, time, duration, title, category, place) in enumerate(itinerary):
        api.call("POST", f"/trips/{trip_id}/itinerary", {
            "id": new_id(trip_id, "item", str(i)),
            "dayDate": days[day].isoformat(),
            "startTime": time,
            "durationMin": duration,
            "title": title,
            "category": category,
            "placeName": place,
            "sortOrder": float(i),
        })

    packing = [
        ("CLOTHING", "Kaos", 3, None), ("CLOTHING", "Jaket tipis", 1, None),
        ("DOCUMENTS", "KTP", 1, None), ("DOCUMENTS", "Tiket kereta", 1, friends[0]),
        ("ELECTRONICS", "Power bank", 1, friends[1]), ("ELECTRONICS", "Charger HP", 1, None),
        ("TOILETRIES", "Sunscreen", 1, me), ("HEALTH", "Obat pribadi", 1, None),
    ]
    for i, (category, name, quantity, who) in enumerate(packing):
        api.call("POST", f"/trips/{trip_id}/packing", {
            "id": new_id(trip_id, "packing", str(i)),
            "category": category,
            "name": name,
            "quantity": quantity,
            "sortOrder": float(i),
            "assigneeMemberId": who,
        })

    expenses = [
        (0, 14, "LODGING", "TRANSFER", "Penginapan 2 malam", 1_200_000, me),
        (0, 13, "FOOD", "CASH", "Gudeg Yu Djum", 165_000, friends[0]),
        (1, 6, "ATTRACTION", "E_WALLET", "Tiket Borobudur", 150_000, me),
        (1, 9, "TRANSPORT", "CASH", "Sewa mobil + sopir", 600_000, friends[1]),
    ]
    for i, (day, hour, category, method, note, amount, payer) in enumerate(expenses):
        api.call("POST", f"/trips/{trip_id}/expenses", {
            "id": new_id(trip_id, "expense", str(i)),
            "amountMinor": amount,
            "currency": "IDR",
            "category": category,
            "paymentMethod": method,
            "note": note,
            "spentAt": instant(days[day], hour),
            "paidByMemberId": payer,
            "splits": even_split(amount, everyone),
        })

    poll_id = new_id(trip_id, "poll")
    poll = api.call("POST", f"/trips/{trip_id}/polls", {
        "id": poll_id,
        "kind": "CUSTOM",
        "question": "Makan malam hari kedua di mana?",
        "closesAt": instant(days[0] - dt.timedelta(days=2), 20),
        "applyWinner": False,
        "options": [
            {"id": new_id(poll_id, "a"), "label": "Bakmi Jowo Mbah Gito"},
            {"id": new_id(poll_id, "b"), "label": "Sate Klathak Pak Pong"},
            {"id": new_id(poll_id, "c"), "label": "Angkringan Kopi Joss"},
        ],
    })
    option = poll["options"][1]
    api.call("PUT", f"/polls/{poll_id}/votes", {"votes": [{"optionId": option["id"], "version": option.get("version", 1)}]})
    return trip_id, days


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--api", required=True, help="API origin, e.g. https://api.ransel.app")
    parser.add_argument("--email", required=True, help="the reviewer account (from SEED_USERS)")
    parser.add_argument("--replace", action="store_true", help="delete the sample trip first and make it again")
    args = parser.parse_args()
    password = os.environ.get("RANSEL_SEED_PASSWORD") or getpass.getpass(f"Password for {args.email}: ")

    api = Api(args.api)
    try:
        api.token = api.call("POST", "/auth/login", {
            "email": args.email, "password": password, "deviceName": "seed-demo-trip",
        })["accessToken"]
        existing = [t["id"] for t in api.call("GET", "/trips") if t["title"] == TITLE]
        if existing and not args.replace:
            print(f"'{TITLE}' already exists ({', '.join(existing)}); nothing to do. Use --replace to make it again.")
            return
        for trip_id in existing:
            api.call("DELETE", f"/trips/{trip_id}")
            print(f"Deleted the old sample trip {trip_id}.")
        trip_id, days = seed(api)
        print(f"Made '{TITLE}' ({trip_id}), {days[0]} to {days[-1]}, for {args.email}.")
    except ApiError as e:
        sys.exit(f"Failed: {e}")


if __name__ == "__main__":
    main()
