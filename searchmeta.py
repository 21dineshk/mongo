from pymongo import MongoClient

# Establish a connection to the MongoDB Atlas cluster
client = MongoClient('your_mongodb_connection_uri')

# Select your database and the specific collection
db = client['your_database_name']
collection = db['your_collection_name']

# Define the aggregation pipeline using the $searchMeta operator
pipeline = [
    {
        '$searchMeta': {
            'index': 'default',  # Replace with your actual Atlas Search index name
            'facet': {
                'operator': {
                    'text': {
                        'query': 'BL65FJ3',  # The householdId you're searching for
                        'path': 'householdId'  # The field you're matching against
                    }
                },
                'facets': {
                    'categoryCounts': {  # This will count occurrences of each category
                        'type': 'string',
                        'path': 'category',  # The field you want to facet on
                        'numBuckets': 100  # Adjust based on the expected diversity of categories
                    }
                }
            }
        }
    }
]

# Execute the aggregation pipeline
results = list(collection.aggregate(pipeline))

# Print the results
for result in results:
    print(result)
