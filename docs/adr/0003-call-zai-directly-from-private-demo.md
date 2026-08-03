---
status: accepted
---

# Call Z.AI directly from the private demo app

The single-device hackathon app calls Z.AI directly with a disposable API key entered by its operator, rather than using a gateway or per-Complaint upload consent. This accepts key-exposure and provider-processing risk for a private, revocable demo credential in exchange for removing backend deployment and keeping the hosted visual path small; the app still sends only one reduced Observation Image per eligible Complaint, validates a fixed Visual Hint, and retains all planning, control, and verification locally. This supersedes ADR 0002.
