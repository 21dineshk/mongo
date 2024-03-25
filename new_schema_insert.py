import certifi
from pymongo import MongoClient
from datetime import datetime, timedelta
import random

# MongoDB setup
client = MongoClient("mongodb+srv://ddinesh:dinesh7981@cluster0.fm9aocc.mongodb.net/?retryWrites=true&w=majority", tlsCAFile=certifi.where())

# Specify your database and collection
db = client['weekly_deals']
collection = db['hh_deals']

# Predefined lists
store_ids = [f"{i:07d}A" for i in range(1, 16)]  # Example store IDs
categories = [f"Category{i}" for i in range(15)]
events = [f"Event{i}" for i in range(15)]
offer_types = [f"OfferType{i}" for i in range(15)]


def random_past_date():
    days_in_past = random.randint(1, 365)  # up to 365 days in the past
    return datetime.now() - timedelta(days=days_in_past)

def random_future_date():
    days_in_future = random.randint(1, 365)  # up to 365 days in the future
    return datetime.now() + timedelta(days=days_in_future)

def random_date():
    days = random.randint(-365, 365)  # past or future up to 365 days
    return datetime.now() + timedelta(days=days)
def generate_doc(household_id, store_id):
    recs = []
    for rank in range(1, 501):  # Generate 10,000 recs
        recs.append({
            "offerId": f"offer{rank}",
            "rank": rank,
            "category": random.choice(categories),
            "event": random.choice(events),
            "offerType": random.choice(offer_types),
            "recentPurchase": random_past_date(),
            "aboutToExpire": random_future_date(),
            "newOffer": random_date()
        })
    return {
        "householdId": household_id,
        "storeId": store_id,
        "recs": recs
    }

def insert_documents(num_docs):
    for _ in range(num_docs):
        for store_id in store_ids:
            household_id = f"{random.randint(1, 1000000):07d}"  # Generate a unique household ID
            doc = generate_doc(household_id, store_id)
            collection.insert_one(doc)

# Insert 1 million documents (adjust the range accordingly)
insert_documents(66667)  # This number multiplied by 15 store IDs equals ~1 million

print("Documents inserted.")
