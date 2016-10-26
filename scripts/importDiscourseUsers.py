#!/usr/bin/python

import psycopg2
import psycopg2.extras
import getpass
import sys

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
print("Importing %d users..." % len(users))
for user in users:
    pass
