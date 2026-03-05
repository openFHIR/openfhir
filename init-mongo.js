// Connect to the admin database to create a user for a specific database
db = db.getSiblingDB('openfhir');

// Create a new user with readWrite access to the 'openfhir' database
db.createUser({
    user: 'openfhir',
    pwd: 'openfhir',
    roles: [
        {
            role: 'readWrite',
            db: 'openfhir'
        }
    ]
});