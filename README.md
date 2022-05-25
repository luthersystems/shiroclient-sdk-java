# Shiroclient Java SDK

This repositroy contains a JSON-RPC client for interacting with a shiroclient
gateway. It only supports the `Call` method presently, and does not support
mock mode.

[![CircleCI](https://circleci.com/gh/luthersystems/shiroclient-sdk-java.svg?style=svg)](https://circleci.com/gh/luthersystems/shiroclient-sdk-java)

Argument configuration is identical to the shiroclient Go implementation.

## Example Usage

Please see the JUnit test which runs against a localhost shiroclient gateway
running on port 8082:

`src/com/luthersystems/shiroclient/ShiroClientTest.java`

It will call the healthcheck function in the phylum and check the response.
