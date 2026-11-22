package com.bbh.itss.coe.oneview.application.discovery;

import com.bbh.itss.coe.oneview.application.UseCase;
import com.bbh.itss.coe.oneview.application.WithoutTransaction;
import com.bbh.itss.coe.oneview.application.discovery.port.in.InspectRepositoryCommand;
import com.bbh.itss.coe.oneview.application.discovery.port.in.InspectRepositoryUseCase;
import com.bbh.itss.coe.oneview.application.discovery.port.out.BitbucketRepositoryPort;
import com.bbh.itss.coe.oneview.domain.discovery.DiscoveredStack;
import com.bbh.itss.coe.oneview.domain.discovery.RepoFileTree;
import com.bbh.itss.coe.oneview.domain.discovery.RepoStackDetector;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class InspectRepositoryService implements InspectRepositoryUseCase {

    private final BitbucketRepositoryPort bitbucketRepository;

    @Override
    @WithoutTransaction
    public DiscoveredStack inspect(InspectRepositoryCommand command) {
        if (command == null || command.repositoryUrl() == null || command.repositoryUrl().isBlank()) {
            return new DiscoveredStack(null, null, null, "VM", null, null, false, null, null, null, null, false, false, java.util.List.of(), "No repository URL provided");
        }
        RepoFileTree tree = bitbucketRepository.fetchRepositoryTree(command.repositoryUrl(), command.branch(), command.credentialsId());
        return RepoStackDetector.detect(tree);
    }
}
