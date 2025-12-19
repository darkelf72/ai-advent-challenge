#!/bin/bash

# Script for stopping MCP servers in Docker containers
# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Stopping MCP Servers${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Get the project root directory (2 levels up from scripts directory)
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$PROJECT_ROOT" || exit 1

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed!${NC}"
    exit 1
fi

# Check if Docker is running
if ! docker info &> /dev/null; then
    echo -e "${RED}Error: Docker is not running!${NC}"
    exit 1
fi

# Check if containers are running
DB_RUNNING=$(docker ps -q --filter "name=db-mcp-server")
HTTP_RUNNING=$(docker ps -q --filter "name=http-mcp-server")

if [ -z "$DB_RUNNING" ] && [ -z "$HTTP_RUNNING" ]; then
    echo -e "${YELLOW}MCP servers are not running.${NC}"
    exit 0
fi

# Stop the containers
echo -e "${GREEN}Stopping db-mcp-server and http-mcp-server...${NC}"
docker stop db-mcp-server http-mcp-server 2>/dev/null

# Check if containers stopped successfully
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}MCP Servers stopped successfully!${NC}"
    echo ""
    exit 0
else
    echo -e "${RED}Failed to stop some MCP servers!${NC}"
    exit 1
fi
