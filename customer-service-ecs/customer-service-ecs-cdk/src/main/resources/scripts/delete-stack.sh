#!/bin/bash
source ../.env
aws cloudformation delete-stack --stack-name QuarkusCustomerManagementStack --region $CDK_DEFAULT_REGION