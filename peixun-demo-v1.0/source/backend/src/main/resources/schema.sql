CREATE TABLE IF NOT EXISTS workspace_state (
  id VARCHAR(80) PRIMARY KEY,
  revision BIGINT NOT NULL,
  epoch INTEGER NOT NULL,
  content CLOB NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS command_receipt (
  workspace_id VARCHAR(80) NOT NULL,
  epoch INTEGER NOT NULL,
  command_id VARCHAR(100) NOT NULL,
  payload_hash VARCHAR(64) NOT NULL,
  response CLOB NOT NULL,
  PRIMARY KEY(workspace_id, epoch, command_id)
);
CREATE TABLE IF NOT EXISTS audit_event (
  id VARCHAR(100) PRIMARY KEY,
  workspace_id VARCHAR(80) NOT NULL,
  epoch INTEGER NOT NULL,
  actor_id VARCHAR(50) NOT NULL,
  action VARCHAR(80) NOT NULL,
  details CLOB NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
