import asyncio
from pymongo import ReadPreference
from motor.motor_asyncio import AsyncIOMotorClient
import time
import certifi

# Connect to MongoDB
client = AsyncIOMotorClient(
    "mongodb+srv://ddinesh:dinesh7981@cluster0.fm9aocc.mongodb.net/?retryWrites=true&w=majority",
    tlsCAFile=certifi.where())
db = client['weekly_deals']
collection = db['hh_deals']
collection_1 = db['hh_deals'].with_options(read_preference=ReadPreference.SECONDARY_PREFERRED)

# Ensure you replace 'someHouseholdId' with an actual householdId from your dataset
household_id = '0862091'


async def query_with_atlas_search_index_1():
    # Construct an aggregation pipeline using the $search stage
    pipeline = [
        {
            "$search": {
                "index": "default",
                "text": {
                    "query": household_id,
                    "path": "householdId"  # Adjust if your search index is configured differently
                }
            }
        }
    ]
    task1 = await collection.aggregate(pipeline).to_list(None)
    return task1


async def query_with_atlas_search_index_2():
    # Construct an aggregation pipeline using the $search stage
    pipeline = [
        {
            "$search": {
                "index": "default",
                "text": {
                    "query": household_id,
                    "path": "householdId"  # Adjust if your search index is configured differently
                }
            },
        },
        {
            "$limit": 30
        }
    ]
    task2 = await collection.aggregate(pipeline).to_list(None)
    return task2


async def test():
    start_time = time.time()
    res1, res2 = await asyncio.gather(query_with_atlas_search_index_1(), query_with_atlas_search_index_2())
    print(f"Time taken without index: {time.time() - start_time} seconds")


asyncio.run(test())
