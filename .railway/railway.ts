import { defineRailway, project, service } from "railway/iac";

export const partial = "pocket-api";

export default defineRailway(() => {
  const api = service("pocket-api", {
    build: { builder: "DOCKERFILE", dockerfilePath: "Dockerfile" },
    healthcheck: "/actuator/health",
    healthcheckTimeout: 180,
    deploy: {
      restartPolicyMaxRetries: 3,
      sleepApplication: true,
    },
  });
  return project("demos", {
    variables: { managed: false },
    resources: [api],
  });
});
