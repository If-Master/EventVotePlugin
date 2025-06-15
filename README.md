# EventVotePlugin

A Easy to use Minecraft plugin for creating and managing server wide event votes and polls. Perfect for community decision-making, event planning, and player engagement.

## Features

### Core Functionality
- **Create Multiple Choice Votes** - Support for 2+ options per vote
- **Flexible Duration System** - Set vote duration from minutes to weeks
- **Real-time Results** - View live vote statistics and progress
- **Anti-Cheat Protection** - Prevents vote manipulation via IP and UUID tracking
- **Multiple Active Votes** - Run several votes simultaneously
- **Auto-Expiration** - Votes automatically end when time runs out

### Server Compatibility
- **Multi-Platform Support** - Works on Bukkit, Spigot, Paper, and Folia
- **Version Support** - Compatible with Minecraft 1.21+
- **Folia Optimized** - Full support for Folia's regionized threading

### Storage Options
- **File Storage** - Simple file-based storage (default)
- **MySQL Database** - Enterprise-grade database support for larger servers
- **Data Persistence** - Votes survive server restarts

### Admin Features
- **Permission System** - Admin controls for vote management
- **Broadcast System** - Server-wide announcements for vote events
- **Vote Management** - Create, start, end, and delete votes
- **Results Display** - Detailed statistics with percentages

## Installation

1. Download the plugin JAR file
2. Place it in your server's `plugins` folder
3. Restart your server
4. Configure the plugin (optional)

## Commands

All commands can be used with `/eventvote`, `/poll`, `/vote`, or `/voting`

### Basic Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/eventvote help` | Show help menu | Default |
| `/eventvote list` | List all active votes | Default |
| `/eventvote <option>` | Cast your vote | Default |

### Vote Management
| Command | Description | Permission | Example |
|---------|-------------|------------|---------|
| `/eventvote create <name> <option1> <option2> [...]` | Create a new vote | Default | `/eventvote create "Server Event" Parkour PvP Building` |
| `/eventvote length <duration>` | Set vote duration | Default | `/eventvote length 1h` |
| `/eventvote start` | Start the most recently created vote | Default | `/eventvote start` |
| `/eventvote end [name]` | End active vote or specific vote | Creator/Admin | `/eventvote end` |
| `/eventvote results <name>` | View vote results | Default | `/eventvote results "Server Event"` |
| `/eventvote delete <name>` | Delete a vote | Creator/Admin | `/eventvote delete "Server Event"` |

### Duration Formats
- `1m` - 1 minute
- `5m` - 5 minutes  
- `30m` - 30 minutes
- `1h` - 1 hour
- `12h` - 12 hours
- `1d` - 1 day
- `1w` - 1 week

## Usage Examples

### Creating a Simple Vote
```
/eventvote create "Next Event" Parkour PvP Building Roleplay
/eventvote length 2h
/eventvote start
```

### Voting Process
```
Player: /eventvote Parkour
Server: §aYour vote for §eParkour§a has been recorded!
```

### Viewing Results
```
/eventvote results "Next Event"

=== Results for: Next Event ===
Created by: AdminPlayer
Status: Active
Total votes: 15
Parkour: 6 (40.0%)
Building: 5 (33.3%)
PvP: 3 (20.0%)
Roleplay: 1 (6.7%)
Time remaining: 1h 23m
```

## Configuration

### config.yml
```yaml
# Storage settings
storage:
  use-mysql: false
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    username: root
    password: ""

# Plugin settings
broadcast-votes: false          # Broadcast vote count updates
auto-remove-expired: true       # Auto-delete expired votes
```

### MySQL Setup
1. Set `use-mysql: true` in config.yml
2. Configure your database credentials
3. Ensure MySQL server is running
4. Plugin will create tables automatically

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `vote.admin` | Delete any vote, end any vote | OP |

**Note:** All players can create, start, and manage their own votes by default.

## Anti-Cheat Features

- **UUID Tracking** - Prevents multiple votes from same player
- **IP Tracking** - Prevents alt account abuse
- **Vote Validation** - Ensures vote options are valid
- **Creator Protection** - Only vote creators (or admins) can end/delete votes/polls

## Technical Details

### File Storage
- Votes saved to `plugins/EventVotePlugin/votes.dat`
- Automatic backup on server shutdown
- Binary serialization for efficiency

### MySQL Storage
- Table: `vote_data`
- Base64 encoded vote objects
- Automatic table creation
- Async database operations

### Performance
- Concurrent data structures for thread safety
- Folia-optimized scheduling
- Minimal server impact
- Efficient vote expiration checking

## Troubleshooting

### Common Issues

**Plugin won't load:**
- Check that main class path matches in plugin.yml
- Ensure Java version compatibility
- Verify all dependencies are present

**Commands not working:**
- Confirm plugin.yml command registration
- Check for command conflicts with other plugins
- Verify permission setup

**MySQL connection failed:**
- Check database credentials in config.yml
- Ensure MySQL server is accessible
- Verify database exists
- Plugin will fallback to file storage automatically

**Votes not persisting:**
- Check file permissions in plugin folder
- For MySQL: verify connection stability
- Check server logs for error messages

## Version History
### v1.0.1
- Permission based creation added

### v1.0
- Initial release
- Basic voting functionality
- File and MySQL storage
- Folia support
- Permission system
- Anti-cheat protection

## Support

For support, bug reports, or feature requests:
- Check server console for error messages
- Verify configuration settings
- Test with minimal plugin setup
- Include server version and plugin version in reports

## License

This plugin is provided as-is for Minecraft server use. Please respect the author's work and don't redistribute without permission.

---

**Author:** Kanuunankuula  
**Version:** 1.0.1  
**Minecraft Version:** 1.21+  
**Server Types:** Bukkit, Spigot, Paper, Folia
