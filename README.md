![Hardcore+ Horizontal Banner](https://cdn.modrinth.com/data/cached_images/a9eaaf0b06d09afac406a5b01754519707223f76.png)

# Description
This plugin will add more challenge to players in survival/hardcore mode, players must depend on each other to not die or all of the players will die even if only one of them die

# Features
- Default (All players will be connected)
- Teamsplit (Using /hpud split, it will split players into team and players dead will only be connected with their teammates)
> ! Teamsplit features was not perfect, you might encounter a bug/error

## Commands

```
/HPUDIDIDUD <help|reload|split|teams|reset|request|accept|decline>

Aliases :
- HardcorePlusUDieIDieIDieUDie
- hpud

 *   help                                       – list all commands
 *   reload                                     – reload config (op)
 *   split <teamSize> <random|choose> [lastadd|lastalone]
 *   random parameter                           – players will be assign randomly with other people into a team
 *   choose parameter                           - players can request other player to be team
 *   reset [world]                              – clear team data (op)
 *   teams [world]                              – show current teams
 *   request <player>                           – (choose mode) request to team with player
 *   accept <player>                            – accept incoming team request
 *   decline <player>                           – decline incoming team request
 *   lastadd parameter                          – last player will be added to the last team if team cannot be divided equally
 *   lastalone parameter                        – last player will be alone if team cannot be divided equally
```

## Configurations

```
# HardcorePlus - U die I die | I die U die
# Worlds listed here will be ignored by the plugin entirely
blacklisted-worlds:
  - "spawn_world"
  - "lobby"

plugin-settings:
  # Whether to broadcast a message when all players are eliminated
  enable-broadcast: true
  broadcast-message: "§c[Hardcore+] All players in {world} have been eliminated!"

  # Does the plugin need hardcore mode to work, default: true
  require-hardcore: true
  
  # How many seconds players have to pick their team in "choose" mode
  # before unmatched players are auto-assigned
  team-request-timeout-seconds: 60
```
<img width="3780" height="800" alt="Hardcore (Banner Horizontal) (6)" src="https://github.com/user-attachments/assets/871ce3d1-2052-4008-ade6-37f178437cb6" />
