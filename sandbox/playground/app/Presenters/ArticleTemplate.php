<?php declare(strict_types=1);

namespace App\Presenters;

use App\Model\Article;

/**
 * The class templates/template-type.latte points at with {templateType}. Its properties are that
 * template's variables, which is what makes them defined without a {var} or a {varType}.
 */
final class ArticleTemplate
{
	public string $heading = 'Articles';

	/** @var Article[] */
	public array $articles = [];
}
