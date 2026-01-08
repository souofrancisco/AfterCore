## Rate limiting / Cooldowns

### Componentes

- `RateLimiter`: interface genérica por chave (`tryAcquire`, `tryAcquireWithRemaining`, `reset`, `clear`).
- `TokenBucketRateLimiter`: implementação (token bucket) com expiração de buckets inativos.
- `CooldownService`: helper para cooldowns por player/UUID + ação.

### Exemplo: cooldown de comando

```java
import java.time.Duration;

CooldownService cooldowns = new CooldownService();

if (cooldowns.tryAcquire(player, "command:warp", Duration.ofSeconds(5))) {
  // executar comando
} else {
  var res = cooldowns.tryAcquireWithRemaining(player, "command:warp", Duration.ofSeconds(5));
  player.sendMessage("Aguarde " + CooldownService.formatDuration(res.remaining()));
}
```

### Exemplo: burst (token bucket)

```java
RateLimiter limiter = TokenBucketRateLimiter.withBurst(
  5, // burst
  Duration.ofSeconds(1) // +1 token por segundo
);

if (!limiter.tryAcquire(player.getUniqueId().toString())) {
  // rate limited
}
```

### Boas práticas

- Use chaves estáveis e descritivas: `command:tpa`, `interact:npc:merchant`, `chat:global`.
- Prefira `CooldownService` quando o caso de uso é “por player”.

