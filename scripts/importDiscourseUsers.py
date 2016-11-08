#!/usr/bin/python

import psycopg2
import psycopg2.extras
import getpass
import sys
from datetime import datetime

# Connect to our input database
dbIn = input("Input database: ")
hostIn = input("Host: ")
userIn = input("Username: ")
pwdIn = getpass.getpass(prompt="Password: ")

try:
    connIn = psycopg2.connect("dbname='%s' user='%s' host='%s' password='%s'" % (dbIn, userIn, hostIn, pwdIn))
except:
    print("Could not connect to input database.")
    sys.exit(1)
print("Connection established.")

# SpongeAuth credentials
dbOut = input("Output database: ")
hostOut = input("Host: ")
userOut = input("User: ")
pwdOut = getpass.getpass(prompt="Password: ")

# Connect
try:
    connOut = psycopg2.connect("dbname='%s' user='%s' host='%s' password='%s'" % (dbOut, userOut, hostOut, pwdOut))
except:
    print("Could not connect to output database.")
    sys.exit(2)
print("Connection established.")

curIn = connIn.cursor(cursor_factory=psycopg2.extras.DictCursor)
curOut = connOut.cursor()

importUsers = len(sys.argv) == 1
if importUsers:
    # Import users
    query = "SELECT * from users WHERE username != 'system';"
    print(query)
    curIn.execute(query)
    users = curIn.fetchall()

    importStmt = ('INSERT INTO users ('
                  'created_at, join_date, email, is_email_confirmed, username, avatar_url, password, salt, is_admin'
                  ') VALUES \n')

    values = "('%s', '%s', '%s', %s, '%s', '/assets/images/spongie.png', '%s', '%s', %s)"
    usersLen = len(users)

    print("Importing %d users..." % usersLen)
    for i, user in enumerate(users):
        created_at = user['created_at']
        username = user['username']
        email = user['email']
        active = user['active']
        password = user['password_hash']
        salt = user['salt']
        admin = user['admin']
        importStmt += values % (datetime.now(), created_at, email, active, username, password, salt, admin)
        if (i < usersLen - 1):
            importStmt += ',\n'

    importStmt += ';'
    print(importStmt)
    curOut.execute(importStmt)

    confirm = input("Commit changes? (y/n): ")
    if confirm.lower() == 'y':
        connOut.commit()

# Import user fields?
importUserFields = len(sys.argv) > 1 and sys.argv[1] == "--customFields"
if importUserFields:
    # Map the custom field names to our corresponding output name
    query = "SELECT DISTINCT name FROM user_custom_fields;"
    print(query)
    curIn.execute(query)
    fieldNames = curIn.fetchall()
    fieldNameMap = {}
    for fieldName in fieldNames:
        outName = input("What is " + fieldName[0] + " called in the output database?: ")
        fieldNameMap[fieldName[0]] = outName

    # Get the usernames for the user IDs we are importing
    query = "SELECT DISTINCT user_id FROM user_custom_fields;"
    print(query)
    curIn.execute(query)
    userIds = curIn.fetchall()
    userMap = {}
    for userId in userIds:
        query = "SELECT username FROM users WHERE id = %d;" % userId[0]
        print(query)
        curIn.execute(query)
        username = curIn.fetchone()
        userMap[userId[0]] = username[0]

    # Import the fields
    query = "SELECT * FROM user_custom_fields;"
    print(query)
    curIn.execute(query)
    fields = curIn.fetchall()
    for field in fields:
        inName = field['name']
        outName = fieldNameMap[inName]
        value = field['value']
        username = userMap[field['user_id']]
        importStmt = "UPDATE users SET %s = '%s' WHERE username = '%s';" % (outName, value, username)
        print(importStmt)
        curOut.execute(importStmt)

    confirm = input("Commit changes? (y/n): ")
    if confirm.lower() == 'y':
        connOut.commit()

# Clean up
curIn.close()
connIn.close()

curOut.close()
connOut.close()
