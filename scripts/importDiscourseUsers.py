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
curIn.execute("SELECT * from users WHERE username != 'system';")
users = curIn.fetchall()
curIn.close()
connIn.close()

importStmt = "INSERT INTO users (created_at, email, username, password, salt, is_admin) VALUES \n"
values = "('%s', '%s', '%s', '%s', '%s', %s)"
usersLen = len(users)

print("Importing %d users..." % usersLen)
for i, user in enumerate(users):
    username = user['username']
    email = user['email']
    password = user['password_hash']
    salt = user['salt']
    admin = user['admin']
    importStmt += values % (datetime.now(), email, username, password, salt, admin)
    if (i < usersLen - 1):
        importStmt += ',\n'

importStmt += ';'
print(importStmt)
curOut = connOut.cursor()
curOut.execute(importStmt)
connOut.commit()

curOut.close()
connOut.close()
