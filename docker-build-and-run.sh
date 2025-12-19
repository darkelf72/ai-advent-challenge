#!/bin/bash

# Script for building and running Docker containers on macOS
# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}AI Advent Challenge - Docker Build & Run${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed!${NC}"
    echo "Please install Docker Desktop for macOS from: https://www.docker.com/products/docker-desktop"
    exit 1
fi

# Check if Docker is running
if ! docker info &> /dev/null; then
    echo -e "${RED}Error: Docker is not running!${NC}"
    echo "Please start Docker Desktop and try again."
    exit 1
fi

echo -e "${GREEN}Docker is installed and running.${NC}"
echo ""

# Check if .env file exists, if not create from example
if [ ! -f .env ]; then
    echo -e "${YELLOW}Warning: .env file not found. Creating from example...${NC}"
    cat > .env << EOF
# API Keys for AI services
YANDEX_API_KEY=your_yandex_api_key_here
GIGA_CHAT_API_KEY=your_giga_chat_api_key_here
EOF
    echo -e "${YELLOW}Please edit .env file and add your API keys before running.${NC}"
    echo ""
fi

# Parse command line arguments
COMMAND=${1:-up}

case $COMMAND in
    build)
        echo -e "${GREEN}Building Docker images...${NC}"
        docker-compose build --no-cache ai-agent local-mcp-server db-mcp-server
        ;;

    up)
        echo -e "${GREEN}Starting services...${NC}"
        docker-compose up -d ai-agent local-mcp-server db-mcp-server
        echo ""
        echo -e "${GREEN}Services started successfully!${NC}"
        echo ""
        echo -e "${GREEN}Access URLs:${NC}"
        echo -e "  - AI Agent:           http://localhost:9999"
        echo -e "  - Local MCP Server:   http://localhost:8080"
        echo -e "  - DB MCP Server:      http://localhost:8081"
        echo ""
        echo -e "${YELLOW}To view logs:${NC} docker-compose logs -f"
        echo -e "${YELLOW}To stop:${NC} ./docker-build-and-run.sh stop"
        ;;

    down|stop)
        echo -e "${GREEN}Stopping services...${NC}"
        docker-compose down
        echo -e "${GREEN}Services stopped.${NC}"
        ;;

    restart)
        echo -e "${GREEN}Restarting services...${NC}"
        docker-compose restart
        echo -e "${GREEN}Services restarted.${NC}"
        ;;

    logs)
        echo -e "${GREEN}Showing logs (Ctrl+C to exit)...${NC}"
        docker-compose logs -f
        ;;

    status)
        echo -e "${GREEN}Service status:${NC}"
        docker-compose ps
        ;;

    clean)
        echo -e "${YELLOW}Cleaning up Docker resources...${NC}"
        docker-compose down -v --remove-orphans
        docker system prune -f
        echo -e "${GREEN}Cleanup completed.${NC}"
        ;;

    rebuild)
        echo -e "${GREEN}Rebuilding and restarting services...${NC}"
        docker-compose down
        docker-compose build --no-cache ai-agent local-mcp-server db-mcp-server
        docker-compose up -d ai-agent local-mcp-server db-mcp-server
        echo -e "${GREEN}Rebuild completed!${NC}"
        ;;

    help|*)
        echo "Usage: ./docker-build-and-run.sh [COMMAND]"
        echo ""
        echo "Commands:"
        echo "  build      - Build Docker images"
        echo "  up         - Start all services (default)"
        echo "  stop       - Stop all services"
        echo "  down       - Stop and remove containers"
        echo "  restart    - Restart all services"
        echo "  logs       - Show and follow logs"
        echo "  status     - Show service status"
        echo "  clean      - Stop services and clean up volumes"
        echo "  rebuild    - Rebuild images and restart services"
        echo "  help       - Show this help message"
        echo ""
        ;;
esac
